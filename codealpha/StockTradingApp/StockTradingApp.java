import java.awt.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.swing.*;

public class StockTradingApp extends JFrame {
    static class Stock implements Serializable {
        String symbol, name;
        double price;
        int available;

        public Stock(String symbol, String name, double price, int available) {
            this.symbol = symbol;
            this.name = name;
            this.price = price;
            this.available = available;
        }

        @Override
        public String toString() {
            return symbol + " - " + name + " | $" + String.format("%.2f", price) + " | Available: " + available;
        }
    }

    static class Transaction implements Serializable {
        String type, symbol, date;
        int quantity;
        double price;

        public Transaction(String type, String symbol, int quantity, double price, String date) {
            this.type = type;
            this.symbol = symbol;
            this.quantity = quantity;
            this.price = price;
            this.date = date;
        }

        @Override
        public String toString() {
            return "[" + date + "] " + type + " " + quantity + " x " + symbol + " @ $" + String.format("%.2f", price);
        }
    }

    static class UserPortfolio implements Serializable {
        double cash = 10000.0;
        Map<String, Integer> holdings = new HashMap<>();
        java.util.List<Transaction> history = new ArrayList<>();
    }

    ArrayList<Stock> stocks = new ArrayList<>();
    UserPortfolio portfolio = new UserPortfolio();
    final String STOCKS_FILE = "stocks.dat";
    final String PORTFOLIO_FILE = "portfolio.dat";

    JTextArea tradeOutput, portfolioOutput, historyOutput;
    JComboBox<String> stockBox;
    JTextField quantityField;
    JLabel cashLabel;

    public StockTradingApp() {
        setTitle("Stock Trading Platform");
        setSize(800, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        JTabbedPane tabs = new JTabbedPane();

        JPanel tradeTab = new JPanel(new BorderLayout(10, 10));
        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        stockBox = new JComboBox<>();
        quantityField = new JTextField(6);
        JButton buyBtn = new JButton("Buy");
        JButton sellBtn = new JButton("Sell");
        JButton refreshBtn = new JButton("Refresh Prices");
        cashLabel = new JLabel("Cash: $10000.00");

        inputPanel.add(new JLabel("Stock:"));
        inputPanel.add(stockBox);
        inputPanel.add(new JLabel("Quantity:"));
        inputPanel.add(quantityField);
        inputPanel.add(buyBtn);
        inputPanel.add(sellBtn);
        inputPanel.add(refreshBtn);

        tradeOutput = new JTextArea();
        tradeOutput.setEditable(false);
        tradeOutput.setFont(new Font("Monospaced", Font.PLAIN, 13));
        JScrollPane tradeScroll = new JScrollPane(tradeOutput);

        JPanel tradeBottom = new JPanel(new BorderLayout());
        tradeBottom.add(cashLabel, BorderLayout.WEST);

        tradeTab.add(inputPanel, BorderLayout.NORTH);
        tradeTab.add(tradeScroll, BorderLayout.CENTER);
        tradeTab.add(tradeBottom, BorderLayout.SOUTH);

        portfolioOutput = new JTextArea();
        portfolioOutput.setEditable(false);
        portfolioOutput.setFont(new Font("Monospaced", Font.PLAIN, 13));
        JScrollPane portfolioScroll = new JScrollPane(portfolioOutput);
        JPanel portfolioTab = new JPanel(new BorderLayout());
        portfolioTab.add(portfolioScroll, BorderLayout.CENTER);

        historyOutput = new JTextArea();
        historyOutput.setEditable(false);
        historyOutput.setFont(new Font("Monospaced", Font.PLAIN, 13));
        JScrollPane historyScroll = new JScrollPane(historyOutput);
        JPanel historyTab = new JPanel(new BorderLayout());
        historyTab.add(historyScroll, BorderLayout.CENTER);

        tabs.addTab("Trade", tradeTab);
        tabs.addTab("Portfolio", portfolioTab);
        tabs.addTab("History", historyTab);
        add(tabs);

        loadData(false);
        refreshStocksBox();
        printStocks();

        buyBtn.addActionListener(e -> {
            String symbol = (String) stockBox.getSelectedItem();
            Stock stock = getStock(symbol);
            int qty;
            try {
                qty = Integer.parseInt(quantityField.getText().trim());
            } catch (Exception ex) {
                tradeOutput.setText("Enter valid quantity.");
                return;
            }
            if (qty <= 0 || qty > stock.available) {
                tradeOutput.setText("Invalid quantity or not enough stock.");
                return;
            }
            double total = qty * stock.price;
            if (portfolio.cash < total) {
                tradeOutput.setText("Not enough cash.");
                return;
            }

            stock.available -= qty;
            portfolio.cash -= total;
            portfolio.holdings.put(symbol, portfolio.holdings.getOrDefault(symbol, 0) + qty);
            String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            portfolio.history.add(new Transaction("BUY", symbol, qty, stock.price, date));
            quantityField.setText("");
            tradeOutput.setText("✅ Bought " + qty + " x " + symbol + " for $" + String.format("%.2f", total));
            cashLabel.setText("Cash: $" + String.format("%.2f", portfolio.cash));
        });

        sellBtn.addActionListener(e -> {
            String symbol = (String) stockBox.getSelectedItem();
            Stock stock = getStock(symbol);
            int qty;
            try {
                qty = Integer.parseInt(quantityField.getText().trim());
            } catch (Exception ex) {
                tradeOutput.setText("Enter valid quantity.");
                return;
            }
            int owned = portfolio.holdings.getOrDefault(symbol, 0);
            if (qty <= 0 || qty > owned) {
                tradeOutput.setText("Invalid quantity or not enough owned.");
                return;
            }

            double total = qty * stock.price;
            stock.available += qty;
            portfolio.cash += total;
            if (qty == owned) portfolio.holdings.remove(symbol);
            else portfolio.holdings.put(symbol, owned - qty);
            String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            portfolio.history.add(new Transaction("SELL", symbol, qty, stock.price, date));
            quantityField.setText("");
            tradeOutput.setText("✅ Sold " + qty + " x " + symbol + " for $" + String.format("%.2f", total));
            cashLabel.setText("Cash: $" + String.format("%.2f", portfolio.cash));
        });

        refreshBtn.addActionListener(e -> {
            simulatePriceChange();
            refreshStocksBox();
            printStocks();
        });

        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == 1) {
                showPortfolio();
            } else if (tabs.getSelectedIndex() == 2) {
                showHistory();
            }
        });
    }

    private Stock getStock(String symbol) {
        for (Stock s : stocks) if (s.symbol.equals(symbol)) return s;
        return null;
    }

    private void refreshStocksBox() {
        stockBox.removeAllItems();
        for (Stock s : stocks) stockBox.addItem(s.symbol);
    }

    private void printStocks() {
        tradeOutput.setText("Available Stocks:\n");
        for (Stock s : stocks) tradeOutput.append(s + "\n");
    }

    private void simulatePriceChange() {
        Random rand = new Random();
        for (Stock s : stocks) {
            double change = (rand.nextDouble() - 0.5) * 10;
            s.price = Math.max(1, s.price + change);
        }
    }

    private void showPortfolio() {
        portfolioOutput.setText("Portfolio:\n");
        for (String sym : portfolio.holdings.keySet()) {
            int qty = portfolio.holdings.get(sym);
            Stock stock = getStock(sym);
            double value = qty * stock.price;
            portfolioOutput.append(sym + ": " + qty + " x $" + String.format("%.2f", stock.price) +
                    " = $" + String.format("%.2f", value) + "\n");
        }
        portfolioOutput.append("\nCash: $" + String.format("%.2f", portfolio.cash));
    }

    private void showHistory() {
        historyOutput.setText("Transaction History:\n");
        for (Transaction t : portfolio.history) {
            historyOutput.append(t + "\n");
        }
    }

    private boolean loadData(boolean showError) {
        try {
            File sf = new File(STOCKS_FILE), pf = new File(PORTFOLIO_FILE);
            if (sf.exists() && pf.exists()) {
                ObjectInputStream sIn = new ObjectInputStream(new FileInputStream(sf));
                ObjectInputStream pIn = new ObjectInputStream(new FileInputStream(pf));
                stocks = (ArrayList<Stock>) sIn.readObject();
                portfolio = (UserPortfolio) pIn.readObject();
                sIn.close();
                pIn.close();
            } else {
                stocks.add(new Stock("AAPL", "Apple Inc.", 180.25, 1000));
                stocks.add(new Stock("GOOG", "Alphabet Inc.", 2500.10, 700));
                stocks.add(new Stock("TSLA", "Tesla Inc.", 700.00, 500));
                stocks.add(new Stock("AMZN", "Amazon.com Inc.", 3300.00, 300));
                stocks.add(new Stock("MSFT", "Microsoft Corp.", 300.50, 800));
            }
            return true;
        } catch (Exception e) {
            if (showError)
                JOptionPane.showMessageDialog(this, "Error loading data.");
            return false;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new StockTradingApp().setVisible(true));
    }
}
