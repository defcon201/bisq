/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.portfolio.bsqswaps;

import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.PeerInfoIconTrading;
import bisq.desktop.components.list.FilterBox;
import bisq.desktop.main.overlays.windows.BsqTradeDetailsWindow;
import bisq.desktop.util.GUIUtil;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.alert.PrivateNotificationManager;
import bisq.core.locale.Res;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.user.Preferences;

import bisq.network.p2p.NodeAddress;

import bisq.common.config.Config;

import com.googlecode.jcsv.writer.CSVEntryConverter;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.fxml.FXML;

import javafx.stage.Stage;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;

import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.Comparator;
import java.util.function.Function;

@FxmlView
public class UnconfirmedBsqSwapsView extends ActivatableViewAndModel<VBox, UnconfirmedBsqSwapsViewModel> {
    private final boolean useDevPrivilegeKeys;

    private enum ColumnNames {
        TRADE_ID(Res.get("shared.tradeId")),
        DATE(Res.get("shared.dateTime")),
        MARKET(Res.get("shared.market")),
        PRICE(Res.get("shared.price")),
        AMOUNT(Res.get("shared.amountWithCur", Res.getBaseCurrencyCode())),
        VOLUME(Res.get("shared.amount")),
        TX_FEE(Res.get("shared.txFee")),
        TRADE_FEE(Res.get("shared.tradeFee")),
        OFFER_TYPE(Res.get("shared.offerType")),
        CONF(Res.get("shared.confirmations"));

        private final String text;

        ColumnNames(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    @FXML
    TableView<UnconfirmedBsqSwapsListItem> tableView;
    @FXML
    TableColumn<UnconfirmedBsqSwapsListItem, UnconfirmedBsqSwapsListItem>
            priceColumn,
            amountColumn,
            volumeColumn,
            txFeeColumn,
            tradeFeeColumn,
            marketColumn,
            directionColumn,
            dateColumn,
            tradeIdColumn,
            confidenceColumn,
            avatarColumn;
    @FXML
    FilterBox filterBox;
    @FXML
    AutoTooltipButton exportButton;
    @FXML
    Label numItems;
    @FXML
    Region footerSpacer;

    private final BsqTradeDetailsWindow window;
    private final Preferences preferences;
    private final PrivateNotificationManager privateNotificationManager;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private SortedList<UnconfirmedBsqSwapsListItem> sortedList;
    private FilteredList<UnconfirmedBsqSwapsListItem> filteredList;
    private ChangeListener<Number> widthListener;

    @Inject
    public UnconfirmedBsqSwapsView(UnconfirmedBsqSwapsViewModel model,
                                   BsqTradeDetailsWindow bsqTradeDetailsWindow,
                                   Preferences preferences,
                                   PrivateNotificationManager privateNotificationManager,
                                   AccountAgeWitnessService accountAgeWitnessService,
                                   @Named(Config.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys) {
        super(model);
        this.window = bsqTradeDetailsWindow;
        this.preferences = preferences;
        this.privateNotificationManager = privateNotificationManager;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.useDevPrivilegeKeys = useDevPrivilegeKeys;
    }

    @Override
    public void initialize() {
        widthListener = (observable, oldValue, newValue) -> onWidthChange((double) newValue);
        txFeeColumn.setGraphic(new AutoTooltipLabel(ColumnNames.TX_FEE.toString()));
        tradeFeeColumn.setGraphic(new AutoTooltipLabel(ColumnNames.TRADE_FEE.toString()));
        priceColumn.setGraphic(new AutoTooltipLabel(ColumnNames.PRICE.toString()));
        amountColumn.setGraphic(new AutoTooltipLabel(ColumnNames.AMOUNT.toString()));
        volumeColumn.setGraphic(new AutoTooltipLabel(ColumnNames.VOLUME.toString()));
        marketColumn.setGraphic(new AutoTooltipLabel(ColumnNames.MARKET.toString()));
        directionColumn.setGraphic(new AutoTooltipLabel(ColumnNames.OFFER_TYPE.toString()));
        dateColumn.setGraphic(new AutoTooltipLabel(ColumnNames.DATE.toString()));
        tradeIdColumn.setGraphic(new AutoTooltipLabel(ColumnNames.TRADE_ID.toString()));
        confidenceColumn.setGraphic(new AutoTooltipLabel(ColumnNames.CONF.toString()));
        avatarColumn.setText("");

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noItems", Res.get("shared.trades"))));

        setTradeIdColumnCellFactory();
        setDirectionColumnCellFactory();
        setAmountColumnCellFactory();
        setTxFeeColumnCellFactory();
        setTradeFeeColumnCellFactory();
        setPriceColumnCellFactory();
        setVolumeColumnCellFactory();
        setDateColumnCellFactory();
        setMarketColumnCellFactory();
        setConfidenceColumnCellFactory();
        setAvatarColumnCellFactory();

        tradeIdColumn.setComparator(Comparator.comparing(o -> o.getBsqSwapTrade().getId()));
        dateColumn.setComparator(Comparator.comparing(o -> o.getBsqSwapTrade().getDate()));
        directionColumn.setComparator(Comparator.comparing(o -> o.getBsqSwapTrade().getOffer().getDirection()));
        marketColumn.setComparator(Comparator.comparing(UnconfirmedBsqSwapsListItem::getMarketLabel));
        priceColumn.setComparator(Comparator.comparing(UnconfirmedBsqSwapsListItem::getPrice, Comparator.nullsFirst(Comparator.naturalOrder())));
        volumeColumn.setComparator(nullsFirstComparingAsTrade(BsqSwapTrade::getVolume));
        amountColumn.setComparator(Comparator.comparing(UnconfirmedBsqSwapsListItem::getAmount, Comparator.nullsFirst(Comparator.naturalOrder())));
        avatarColumn.setComparator(Comparator.comparing(UnconfirmedBsqSwapsListItem::getNumPastTrades, Comparator.nullsFirst(Comparator.naturalOrder())));
        txFeeColumn.setComparator(nullsFirstComparing(BsqSwapTrade::getTxFeePerVbyte));
        txFeeColumn.setComparator(Comparator.comparing(UnconfirmedBsqSwapsListItem::getTxFee, Comparator.nullsFirst(Comparator.naturalOrder())));

        //
        tradeFeeColumn.setComparator(Comparator.comparing(item -> {
            String tradeFee = item.getTradeFeeAsString();
            // We want to separate BSQ and BTC fees so we use a prefix
            if (item.getBsqSwapTrade().getOffer().isCurrencyForMakerFeeBtc()) {
                return "BTC" + tradeFee;
            } else {
                return "BSQ" + tradeFee;
            }
        }, Comparator.nullsFirst(Comparator.naturalOrder())));
        confidenceColumn.setComparator(Comparator.comparing(UnconfirmedBsqSwapsListItem::getConfidence));

        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(dateColumn);

        numItems.setId("num-offers");
        numItems.setPadding(new Insets(-5, 0, 0, 10));
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        HBox.setMargin(exportButton, new Insets(0, 10, 0, 0));
        exportButton.updateText(Res.get("shared.exportCSV"));
    }

    @Override
    protected void activate() {
        filteredList = new FilteredList<>(model.dataModel.getList());

        sortedList = new SortedList<>(filteredList);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());

        tableView.setItems(sortedList);

        filterBox.initialize(filteredList, tableView); // here because filteredList is instantiated here
        filterBox.activate();

        numItems.setText(Res.get("shared.numItemsLabel", sortedList.size()));
        exportButton.setOnAction(event -> {
            CSVEntryConverter<UnconfirmedBsqSwapsListItem> headerConverter = item -> {
                String[] columns = new String[ColumnNames.values().length];
                for (ColumnNames m : ColumnNames.values()) {
                    columns[m.ordinal()] = m.toString();
                }
                return columns;
            };
            CSVEntryConverter<UnconfirmedBsqSwapsListItem> contentConverter = item -> {
                String[] columns = new String[ColumnNames.values().length];
                columns[ColumnNames.TRADE_ID.ordinal()] = item.getTradeId();
                columns[ColumnNames.DATE.ordinal()] = item.getDateAsString();
                columns[ColumnNames.MARKET.ordinal()] = item.getMarketLabel();
                columns[ColumnNames.PRICE.ordinal()] = item.getPriceAsString();
                columns[ColumnNames.AMOUNT.ordinal()] = item.getAmountAsString();
                columns[ColumnNames.VOLUME.ordinal()] = item.getVolumeAsString();
                columns[ColumnNames.TX_FEE.ordinal()] = item.getTxFeeAsString();
                columns[ColumnNames.TRADE_FEE.ordinal()] = item.getTradeFeeAsString();
                columns[ColumnNames.OFFER_TYPE.ordinal()] = item.getDirectionLabel();
                columns[ColumnNames.CONF.ordinal()] = String.valueOf(item.getConfidence());
                return columns;
            };

            GUIUtil.exportCSV("bsqSwapHistory.csv", headerConverter, contentConverter,
                    null, sortedList, (Stage) root.getScene().getWindow());
        });

        root.widthProperty().addListener(widthListener);
        onWidthChange(root.getWidth());
    }

    @Override
    protected void deactivate() {
        sortedList.comparatorProperty().unbind();
        exportButton.setOnAction(null);

        filterBox.deactivate();
        root.widthProperty().removeListener(widthListener);
    }

    private static <T extends Comparable<T>> Comparator<UnconfirmedBsqSwapsListItem> nullsFirstComparing(
            Function<BsqSwapTrade, T> keyExtractor) {
        return Comparator.comparing(
                o -> o.getBsqSwapTrade() != null ? keyExtractor.apply(o.getBsqSwapTrade()) : null,
                Comparator.nullsFirst(Comparator.naturalOrder())
        );
    }

    private static <T extends Comparable<T>> Comparator<UnconfirmedBsqSwapsListItem> nullsFirstComparingAsTrade(
            Function<BsqSwapTrade, T> keyExtractor) {
        return Comparator.comparing(
                o -> keyExtractor.apply(o.getBsqSwapTrade()),
                Comparator.nullsFirst(Comparator.naturalOrder())
        );
    }

    private void onWidthChange(double width) {
        txFeeColumn.setVisible(width > 1200);
        tradeFeeColumn.setVisible(width > 1300);
    }

    private void setTradeIdColumnCellFactory() {
        tradeIdColumn.getStyleClass().add("first-column");
        tradeIdColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper<>(offerListItem.getValue()));
        tradeIdColumn.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<UnconfirmedBsqSwapsListItem, UnconfirmedBsqSwapsListItem> call(TableColumn<UnconfirmedBsqSwapsListItem,
                            UnconfirmedBsqSwapsListItem> column) {
                        return new TableCell<>() {
                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final UnconfirmedBsqSwapsListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    field = new HyperlinkWithIcon(item.getTradeId());
                                    field.setOnAction(event -> {
                                        window.show(item.getBsqSwapTrade());
                                    });
                                    field.setTooltip(new Tooltip(Res.get("tooltip.openPopupForDetails")));
                                    setGraphic(field);
                                } else {
                                    setGraphic(null);
                                    if (field != null)
                                        field.setOnAction(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setDateColumnCellFactory() {
        dateColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        dateColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<UnconfirmedBsqSwapsListItem, UnconfirmedBsqSwapsListItem> call(
                            TableColumn<UnconfirmedBsqSwapsListItem, UnconfirmedBsqSwapsListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final UnconfirmedBsqSwapsListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setGraphic(new AutoTooltipLabel(item.getDateAsString()));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
    }

    private void setMarketColumnCellFactory() {
        marketColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        marketColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<UnconfirmedBsqSwapsListItem, UnconfirmedBsqSwapsListItem> call(
                            TableColumn<UnconfirmedBsqSwapsListItem, UnconfirmedBsqSwapsListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final UnconfirmedBsqSwapsListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setGraphic(new AutoTooltipLabel(item.getMarketLabel()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setConfidenceColumnCellFactory() {
        confidenceColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        confidenceColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<UnconfirmedBsqSwapsListItem, UnconfirmedBsqSwapsListItem> call(
                            TableColumn<UnconfirmedBsqSwapsListItem, UnconfirmedBsqSwapsListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final UnconfirmedBsqSwapsListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    setGraphic(item.getTxConfidenceIndicator());
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    @SuppressWarnings("UnusedReturnValue")
    private TableColumn<UnconfirmedBsqSwapsListItem, UnconfirmedBsqSwapsListItem> setAvatarColumnCellFactory() {
        avatarColumn.getStyleClass().addAll("last-column", "avatar-column");
        avatarColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        avatarColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<UnconfirmedBsqSwapsListItem, UnconfirmedBsqSwapsListItem> call(TableColumn<UnconfirmedBsqSwapsListItem, UnconfirmedBsqSwapsListItem> column) {
                        return new TableCell<>() {

                            @Override
                            public void updateItem(final UnconfirmedBsqSwapsListItem newItem, boolean empty) {
                                super.updateItem(newItem, empty);

                                if (newItem != null && !empty/* && newItem.getAtomicTrade() instanceof Trade*/) {
                                    var bsqSwapTrade = newItem.getBsqSwapTrade();
                                    int numPastTrades = newItem.getNumPastTrades();
                                    final NodeAddress tradingPeerNodeAddress = bsqSwapTrade.getTradingPeerNodeAddress();
                                    String role = Res.get("peerInfoIcon.tooltip.tradePeer");
                                    Node peerInfoIcon = new PeerInfoIconTrading(tradingPeerNodeAddress,
                                            role,
                                            numPastTrades,
                                            privateNotificationManager,
                                            bsqSwapTrade.getOffer(),
                                            preferences,
                                            accountAgeWitnessService,
                                            useDevPrivilegeKeys);
                                    setPadding(new Insets(1, 15, 0, 0));
                                    setGraphic(peerInfoIcon);
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
        return avatarColumn;
    }

    private void setAmountColumnCellFactory() {
        amountColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        amountColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<UnconfirmedBsqSwapsListItem, UnconfirmedBsqSwapsListItem> call(
                            TableColumn<UnconfirmedBsqSwapsListItem, UnconfirmedBsqSwapsListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final UnconfirmedBsqSwapsListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setGraphic(new AutoTooltipLabel(item.getAmountAsString()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setPriceColumnCellFactory() {
        priceColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        priceColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<UnconfirmedBsqSwapsListItem, UnconfirmedBsqSwapsListItem> call(
                            TableColumn<UnconfirmedBsqSwapsListItem, UnconfirmedBsqSwapsListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final UnconfirmedBsqSwapsListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setGraphic(new AutoTooltipLabel(item.getPriceAsString()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setVolumeColumnCellFactory() {
        volumeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        volumeColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<UnconfirmedBsqSwapsListItem, UnconfirmedBsqSwapsListItem> call(
                            TableColumn<UnconfirmedBsqSwapsListItem, UnconfirmedBsqSwapsListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final UnconfirmedBsqSwapsListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setGraphic(new AutoTooltipLabel(item.getVolumeAsString()));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
    }

    private void setDirectionColumnCellFactory() {
        directionColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        directionColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<UnconfirmedBsqSwapsListItem, UnconfirmedBsqSwapsListItem> call(
                            TableColumn<UnconfirmedBsqSwapsListItem, UnconfirmedBsqSwapsListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final UnconfirmedBsqSwapsListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setGraphic(new AutoTooltipLabel(item.getDirectionLabel()));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
    }

    private void setTxFeeColumnCellFactory() {
        txFeeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        txFeeColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<UnconfirmedBsqSwapsListItem, UnconfirmedBsqSwapsListItem> call(
                            TableColumn<UnconfirmedBsqSwapsListItem, UnconfirmedBsqSwapsListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final UnconfirmedBsqSwapsListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setGraphic(new AutoTooltipLabel(item.getTxFeeAsString()));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
    }

    private void setTradeFeeColumnCellFactory() {
        tradeFeeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        tradeFeeColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<UnconfirmedBsqSwapsListItem, UnconfirmedBsqSwapsListItem> call(
                            TableColumn<UnconfirmedBsqSwapsListItem, UnconfirmedBsqSwapsListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final UnconfirmedBsqSwapsListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setGraphic(new AutoTooltipLabel(item.getTradeFeeAsString()));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
    }
}
