// Copyright (c) 2016-2017 Ivan Vaklinov <ivan@vaklinov.com>
// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

import org.myhush.gui.HushDaemonObserver.DAEMON_STATUS;
import org.myhush.gui.HushDaemonObserver.DaemonInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

class DashboardPanel extends WalletTabPanel {
    private final JFrame parentFrame;
    private final HushDaemonObserver installationObserver;
    private final HushCommandLineBridge clientCaller;
    private final StatusUpdateErrorReporter errorReporter;

    private JLabel networkAndBlockchainLabel;
    private DataGatheringThread<HushCommandLineBridge.NetworkAndBlockchainInfo> netInfoGatheringThread;

    private Boolean walletIsEncrypted = null;
    private Integer blockchainPercentage = null;

    private String OSInfo = null;
    private JLabel daemonStatusLabel;
    private DataGatheringThread<DaemonInfo> daemonInfoGatheringThread;

    private JLabel walletBalanceLabel;
    private DataGatheringThread<HushCommandLineBridge.WalletBalance> walletBalanceGatheringThread;

    private JScrollPane transactionsTablePane;
    private String[][] lastTransactionsData;
    private DataGatheringThread<String[][]> transactionGatheringThread;

    DashboardPanel(JFrame parentFrame,
                   HushDaemonObserver installationObserver,
                   HushCommandLineBridge clientCaller,
                   StatusUpdateErrorReporter errorReporter
    ) throws IOException, InterruptedException, HushCommandLineBridge.WalletCallException {
        this.parentFrame = parentFrame;
        this.installationObserver = installationObserver;
        this.clientCaller = clientCaller;
        this.errorReporter = errorReporter;

        this.timers = new ArrayList<>();
        this.threads = new ArrayList<>();

        // Build content
        JPanel dashboard = this;
        dashboard.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        dashboard.setLayout(new BorderLayout(0, 0));

        // Upper panel with wallet balance
        JPanel balanceStatusPanel = new JPanel();
        // Use border layout to have balances to the left
        balanceStatusPanel.setLayout(new BorderLayout(3, 3));
        //balanceStatusPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));

        JPanel tempPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 13, 3));
        JLabel logoLabel = new JLabel(new ImageIcon(
                this.getClass().getClassLoader().getResource("images/hush-logo-sm.png")));
        tempPanel.add(logoLabel);
        //tempPanel.add(new JLabel(" "));
        JLabel zcLabel = new JLabel("HUSH Wallet     ");
        zcLabel.setFont(new Font("Helvetica", Font.BOLD | Font.ITALIC, 32));
        tempPanel.add(zcLabel);
        //tempPanel.setToolTipText("Powered by Hush\u00AE");
        balanceStatusPanel.add(tempPanel, BorderLayout.WEST);

        JLabel transactionHeadingLabel = new JLabel(
                "<html><span style=\"font-size:23px\"><br/></span>Transactions:</html>");
        transactionHeadingLabel.setFont(new Font("Helvetica", Font.BOLD, 19));
        balanceStatusPanel.add(transactionHeadingLabel, BorderLayout.CENTER);

        PresentationPanel walletBalancePanel = new PresentationPanel();
        walletBalancePanel.add(walletBalanceLabel = new JLabel());
        balanceStatusPanel.add(walletBalancePanel, BorderLayout.EAST);

        dashboard.add(balanceStatusPanel, BorderLayout.NORTH);

        // Table of transactions
        lastTransactionsData = getTransactionsDataFromWallet();
        dashboard.add(transactionsTablePane = new JScrollPane(this.createTransactionsTable(lastTransactionsData)), BorderLayout.CENTER);

        // Lower panel with installation status
        JPanel installationStatusPanel = new JPanel();
        installationStatusPanel.setLayout(new BorderLayout(3, 3));
        //installationStatusPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        PresentationPanel daemonStatusPanel = new PresentationPanel();
        daemonStatusPanel.add(daemonStatusLabel = new JLabel());
        installationStatusPanel.add(daemonStatusPanel, BorderLayout.WEST);

        PresentationPanel networkAndBlockchainPanel = new PresentationPanel();
        networkAndBlockchainPanel.add(networkAndBlockchainLabel = new JLabel());
        installationStatusPanel.add(networkAndBlockchainPanel, BorderLayout.EAST);

        dashboard.add(installationStatusPanel, BorderLayout.SOUTH);

        // Thread and timer to update the daemon status
        this.daemonInfoGatheringThread = new DataGatheringThread<>(
                () -> {
                    long start = System.currentTimeMillis();
                    DaemonInfo daemonInfo = DashboardPanel.this.installationObserver.getDaemonInfo();
                    long end = System.currentTimeMillis();
                    System.out.println("Gathering of dashboard daemon status data done in " + (end - start) + "ms.");

                    return daemonInfo;
                },
                this.errorReporter, 2000, true
        );
        this.threads.add(this.daemonInfoGatheringThread);

        ActionListener alDeamonStatus = actionEvent -> {
            try {
                DashboardPanel.this.updateDaemonStatusLabel();
            } catch (Exception ex) {
                ex.printStackTrace();
                DashboardPanel.this.errorReporter.reportError(ex);
            }
        };
        Timer timer = new Timer(1000, alDeamonStatus);
        timer.start();
        this.timers.add(timer);

        // Thread and timer to update the wallet balance
        this.walletBalanceGatheringThread = new DataGatheringThread<>(
                () -> {
                    long start = System.currentTimeMillis();
                    HushCommandLineBridge.WalletBalance balance = DashboardPanel.this.clientCaller.getWalletInfo();
                    long end = System.currentTimeMillis();

                    // TODO: move this call to a dedicated one-off gathering thread - this is the wrong place
                    // it works but a better design is needed.
                    if (DashboardPanel.this.walletIsEncrypted == null) {
                        DashboardPanel.this.walletIsEncrypted = DashboardPanel.this.clientCaller.isWalletEncrypted();
                    }

                    System.out.println("Gathering of dashboard wallet balance data done in " + (end - start) + "ms.");

                    return balance;
                },
                this.errorReporter, 8000, true
        );
        this.threads.add(this.walletBalanceGatheringThread);

        ActionListener alWalletBalance = e -> {
            try {
                DashboardPanel.this.updateWalletStatusLabel();
            } catch (Exception ex) {
                ex.printStackTrace();
                DashboardPanel.this.errorReporter.reportError(ex);
            }
        };
        Timer walletBalanceTimer = new Timer(2000, alWalletBalance);
        walletBalanceTimer.setInitialDelay(1000);
        walletBalanceTimer.start();
        this.timers.add(walletBalanceTimer);

        // Thread and timer to update the transactions table
        this.transactionGatheringThread = new DataGatheringThread<>(
                () -> {
                    long start = System.currentTimeMillis();
                    String[][] data = DashboardPanel.this.getTransactionsDataFromWallet();
                    long end = System.currentTimeMillis();
                    System.out.println("Gathering of dashboard wallet transactions table data done in " + (end - start) + "ms.");

                    return data;
                },
                this.errorReporter, 25000
        );
        this.threads.add(this.transactionGatheringThread);

        ActionListener alTransactions = e -> {
            try {
                DashboardPanel.this.updateWalletTransactionsTable();
            } catch (Exception ex) {
                ex.printStackTrace();
                DashboardPanel.this.errorReporter.reportError(ex);
            }
        };
        timer = new Timer(5000, alTransactions);
        timer.start();
        this.timers.add(timer);

        // Thread and timer to update the network and blockchain details
        this.netInfoGatheringThread = new DataGatheringThread<>(
                () -> {
                    long start = System.currentTimeMillis();
                    HushCommandLineBridge.NetworkAndBlockchainInfo data = DashboardPanel.this.clientCaller.getNetworkAndBlockchainInfo();
                    long end = System.currentTimeMillis();
                    System.out.println("Gathering of network and blockchain info data done in " + (end - start) + "ms.");

                    return data;
                },
                this.errorReporter, 10000, true
        );
        this.threads.add(this.netInfoGatheringThread);

        ActionListener alNetAndBlockchain = e -> {
            try {
                DashboardPanel.this.updateNetworkAndBlockchainLabel();
            } catch (Exception ex) {
                ex.printStackTrace();
                DashboardPanel.this.errorReporter.reportError(ex);
            }
        };
        Timer netAndBlockchainTimer = new Timer(5000, alNetAndBlockchain);
        netAndBlockchainTimer.setInitialDelay(1000);
        netAndBlockchainTimer.start();
        this.timers.add(netAndBlockchainTimer);
    }

    // May be null!
    public Integer getBlockchainPercentage() {
        return this.blockchainPercentage;
    }

    private void updateDaemonStatusLabel()
            throws IOException, InterruptedException {
        DaemonInfo daemonInfo = this.daemonInfoGatheringThread.getLastData();

        // It is possible there has been no gathering initially
        if (daemonInfo == null) {
            return;
        }

        String daemonStatus = "<span style=\"color:green;font-weight:bold\">RUNNING</span>";
        if (daemonInfo.status != DAEMON_STATUS.RUNNING) {
            daemonStatus = "<span style=\"color:red;font-weight:bold\">NOT RUNNING</span>";
        }

        String runtimeInfo = "";

        // If the virtual size/CPU are 0 - do not show them
        String virtual = "";
        if (daemonInfo.virtualSizeMB > 0) {
            virtual = ", Virtual: " + daemonInfo.virtualSizeMB + " MB";
        }

        String cpuPercentage = "";
        if (daemonInfo.cpuPercentage > 0) {
            cpuPercentage = ", CPU: " + daemonInfo.cpuPercentage + "%";
        }

        if (daemonInfo.status == DAEMON_STATUS.RUNNING) {
            runtimeInfo = "<span style=\"font-size:8px\">" +
                                  "Resident: " + daemonInfo.residentSizeMB + " MB" + virtual +
                                  cpuPercentage + "</span>";
        }

        // TODO: what if Hush directory is non-default...
        File walletDAT = new File(OSUtil.getBlockchainDirectory() + "/wallet.dat");

        if (this.OSInfo == null) {
            this.OSInfo = OSUtil.getSystemInfo();
        }

        String walletEncryption = "";
        // TODO: Use a one-off data gathering thread - better design
        if (this.walletIsEncrypted != null) {
            walletEncryption =
                    "<span style=\"font-size:8px\">" +
                            " (" + (this.walletIsEncrypted ? "" : "not ") + "encrypted)" +
                            "</span>";
        }

        String text =
                "<html><span style=\"font-weight:bold;color:#303030\">hushd</span> status: " +
                        daemonStatus + ",  " + runtimeInfo + " <br/>" +
                        "Wallet: <span style=\"font-weight:bold;color:#303030\">" + walletDAT.getCanonicalPath() + "</span>" +
                        walletEncryption + " <br/> " +
                        "<span style=\"font-size:3px\"><br/></span>" +
                        "<span style=\"font-size:8px\">" +
                        "Installation: " + OSUtil.getProgramDirectory() + ", " +
                        "Blockchain: " + OSUtil.getBlockchainDirectory() + " <br/> " +
                        "System: " + this.OSInfo + " </span> </html>";
        this.daemonStatusLabel.setText(text);
    }


    private void updateNetworkAndBlockchainLabel() {
        HushCommandLineBridge.NetworkAndBlockchainInfo info = this.netInfoGatheringThread.getLastData();

        // It is possible there has been no gathering initially
        if (info == null) {
            return;
        }

        final Date startDate = new Date("18 Nov 2016 01:53:31 GMT");
        final Date nowDate = new Date(System.currentTimeMillis());

        long fullTime = nowDate.getTime() - startDate.getTime();
        long remainingTime = nowDate.getTime() - info.lastBlockDate.getTime();

        String percentage = "100";
        if (remainingTime > 20 * 60 * 1000) // After 20 min we report 100% anyway
        {
            double dPercentage = 100d - (((double) remainingTime / (double) fullTime) * 100d);
            if (dPercentage < 0) {
                dPercentage = 0;
            } else if (dPercentage > 100d) {
                dPercentage = 100d;
            }

            DecimalFormat df = new DecimalFormat("##0.##");
            percentage = df.format(dPercentage);

            // Also set a member that may be queried
            this.blockchainPercentage = (int) dPercentage;
        } else {
            this.blockchainPercentage = 100;
        }

        // Just in case early on the call returns some junk date
        if (info.lastBlockDate.before(startDate)) {
            // TODO: write log that we fix minimum date! - this condition should not occur
            info.lastBlockDate = startDate;
        }

        String connections = " \u26D7";
        String tickSymbol = " \u2705";
        OSUtil.OS_TYPE os = OSUtil.getOSType();
        // Handling special symbols on Mac OS/Windows
        // TODO: isolate OS-specific symbol stuff in separate code
        if ((os == OSUtil.OS_TYPE.MAC_OS) || (os == OSUtil.OS_TYPE.WINDOWS)) {
            connections = " \u21D4";
            tickSymbol = " \u2606";
        }

        String tick = "";
        if (percentage.equals("100")) {
            tick = "<span style=\"font-weight:bold;font-size:12px;color:green\">" + tickSymbol + "</span>";
        }

        String netColor = "red";
        if (info.numConnections > 0) {
            netColor = "#cc3300";
        }

        if (info.numConnections > 2) {
            netColor = "black";
        }

        if (info.numConnections > 6) {
            netColor = "green";
        }

        String text =
                "<html> " +
                        "Blockchain synchronized: <span style=\"font-weight:bold\">" +
                        percentage + "% </span> " + tick + " <br/>" +
                        "Up to: <span style=\"font-size:8px;font-weight:bold\">" +
                        info.lastBlockDate.toLocaleString() + "</span>  <br/> " +
                        "<span style=\"font-size:1px\"><br/></span>" +
                        "Network: <span style=\"font-weight:bold\">" + info.numConnections + " connections</span>" +
                        "<span style=\"font-size:16px;color:" + netColor + "\">" + connections + "</span>";
        this.networkAndBlockchainLabel.setText(text);
    }


    private void updateWalletStatusLabel() {
        HushCommandLineBridge.WalletBalance balance = this.walletBalanceGatheringThread.getLastData();

        // It is possible there has been no gathering initially
        if (balance == null) {
            return;
        }

        // Format double numbers - else sometimes we get exponential notation 1E-4 ZEC
        DecimalFormat df = new DecimalFormat("########0.00######");

        String transparentBalance = df.format(balance.transparentBalance);
        String privateBalance = df.format(balance.privateBalance);
        String totalBalance = df.format(balance.totalBalance);

        String transparentUCBalance = df.format(balance.transparentUnconfirmedBalance);
        String privateUCBalance = df.format(balance.privateUnconfirmedBalance);
        String totalUCBalance = df.format(balance.totalUnconfirmedBalance);

        String color1 = transparentBalance.equals(transparentUCBalance) ? "" : "color:#cc3300;";
        String color2 = privateBalance.equals(privateUCBalance) ? "" : "color:#cc3300;";
        String color3 = totalBalance.equals(totalUCBalance) ? "" : "color:#cc3300;";

        String text =
                "<html>" +
                        "<span style=\"font-family:monospace;font-size:8.9px;" + color1 + "\">Transparent balance: <span style=\"font-size:9px\">" +
                        transparentUCBalance + " HUSH </span></span><br/> " +
                        "<span style=\"font-family:monospace;font-size:8.9px;" + color2 + "\">Private (Z) balance: <span style=\"font-weight:bold;font-size:9px\">" +
                        privateUCBalance + " HUSH </span></span><br/> " +
                        "<span style=\"font-family:monospace;font-size:8.9px;" + color3 + "\">Total (Z+T) balance: <span style=\"font-weight:bold;font-size:11.5px;\">" +
                        totalUCBalance + " HUSH </span></span>" +
                        "<br/>  </html>";

        this.walletBalanceLabel.setText(text);

        String toolTip = null;
        if ((!transparentBalance.equals(transparentUCBalance)) ||
                    (!privateBalance.equals(privateUCBalance)) ||
                    (!totalBalance.equals(totalUCBalance))) {
            toolTip = "<html>" +
                              "Unconfirmed (unspendable) balance is being shown due to an<br/>" +
                              "ongoing transaction! Actual confirmed (spendable) balance is:<br/>" +
                              "<span style=\"font-size:5px\"><br/></span>" +
                              "Transparent: " + transparentBalance + " HUSH<br/>" +
                              "Private ( Z ): <span style=\"font-weight:bold\">" + privateBalance + " HUSH</span><br/>" +
                              "Total ( Z+T ): <span style=\"font-weight:bold\">" + totalBalance + " HUSH</span>" +
                              "</html>";
        }

        this.walletBalanceLabel.setToolTipText(toolTip);
    }


    private void updateWalletTransactionsTable() {
        String[][] newTransactionsData = this.transactionGatheringThread.getLastData();

        // May be null - not even gathered once
        if (newTransactionsData == null) {
            return;
        }

        if (!Arrays.deepEquals(lastTransactionsData, newTransactionsData)) {
            System.out.println("Updating table of transactions...");
            this.remove(transactionsTablePane);
            this.add(transactionsTablePane = new JScrollPane(this.createTransactionsTable(newTransactionsData)), BorderLayout.CENTER);
        }

        lastTransactionsData = newTransactionsData;

        this.validate();
        this.repaint();
    }


    private JTable createTransactionsTable(String rowData[][]) {
        String columnNames[] = { "Type", "Direction", "Confirmed?", "Amount", "Date", "Destination Address" };
        JTable table = new TransactionTable(
                rowData, columnNames, this.parentFrame, this.clientCaller);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.getColumnModel().getColumn(0).setPreferredWidth(190);
        table.getColumnModel().getColumn(1).setPreferredWidth(145);
        table.getColumnModel().getColumn(2).setPreferredWidth(170);
        table.getColumnModel().getColumn(3).setPreferredWidth(210);
        table.getColumnModel().getColumn(4).setPreferredWidth(405);
        table.getColumnModel().getColumn(5).setPreferredWidth(800);

        return table;
    }


    private String[][] getTransactionsDataFromWallet()
            throws HushCommandLineBridge.WalletCallException, IOException, InterruptedException {
        // Get available public+private transactions and unify them.
        String[][] publicTransactions = this.clientCaller.getWalletPublicTransactions();
        String[][] zReceivedTransactions = this.clientCaller.getWalletZReceivedTransactions();

        String[][] allTransactions = new String[publicTransactions.length + zReceivedTransactions.length][];

        int i = 0;

        for (String[] t : publicTransactions) {
            allTransactions[i++] = t;
        }

        for (String[] t : zReceivedTransactions) {
            allTransactions[i++] = t;
        }

        // Sort transactions by date
        Arrays.sort(allTransactions, (o1, o2) -> {
            Date d1 = new Date(0);
            if (!o1[4].equals("N/A")) {
                d1 = new Date(Long.valueOf(o1[4]) * 1000L);
            }

            Date d2 = new Date(0);
            if (!o2[4].equals("N/A")) {
                d2 = new Date(Long.valueOf(o2[4]) * 1000L);
            }

            if (d1.equals(d2)) {
                return 0;
            } else {
                return d2.compareTo(d1);
            }
        });


        // Confirmation symbols
        String confirmed = "\u2690";
        String notConfirmed = "\u2691";

        // Windows does not support the flag symbol (Windows 7 by default)
        // TODO: isolate OS-specific symbol codes in a separate class
        OSUtil.OS_TYPE os = OSUtil.getOSType();
        if (os == OSUtil.OS_TYPE.WINDOWS) {
            confirmed = " \u25B7";
            notConfirmed = " \u25B6";
        }

        DecimalFormat df = new DecimalFormat("########0.00######");

        // Change the direction and date etc. attributes for presentation purposes
        for (String[] trans : allTransactions) {
            // Direction
            switch (trans[1]) {
                case "receive":
                    trans[1] = "\u21E8 IN";
                    break;
                case "send":
                    trans[1] = "\u21E6 OUT";
                    break;
                case "generate":
                    trans[1] = "\u2692\u2699 MINED";
                    break;
                case "immature":
                    trans[1] = "\u2696 Immature";
                    break;
            }

            // Date
            if (!trans[4].equals("N/A")) {
                trans[4] = new Date(Long.valueOf(trans[4]) * 1000L).toLocaleString();
            }

            // Amount
            try {
                double amount = Double.valueOf(trans[3]);
                if (amount < 0d) {
                    amount = -amount;
                }
                trans[3] = df.format(amount);
            } catch (final NumberFormatException e) {
                System.out.println("Error occurred while formatting amount: " + trans[3] +
                                           " - " + e.getMessage() + "!");
            }

            // Confirmed?
            try {
                boolean isConfirmed = !trans[2].trim().equals("0");

                trans[2] = isConfirmed ? ("Yes " + confirmed) : ("No  " + notConfirmed);
            } catch (final NumberFormatException e) {
                System.out.println("Error occurred while formatting confirmations: " + trans[2] +
                                           " - " + e.getMessage() + "!");
            }
        }

        return allTransactions;
    }

}
