// Copyright (c) 2016-2017 Ivan Vaklinov <ivan@vaklinov.com>
// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

import org.myhush.gui.HushDaemonObserver.DAEMON_STATUS;
import org.myhush.gui.HushDaemonObserver.DaemonInfo;
import org.myhush.gui.HushDaemonObserver.InstallationDetectionException;

import javax.swing.*;
import javax.swing.UIManager.LookAndFeelInfo;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;

/**
 * Main wallet window
 */
class HushWalletFrame extends JFrame {
    private final StatusUpdateErrorReporter errorReporter;

    private WalletOperations walletOps;

    private DashboardPanel dashboard;
    private AddressesPanel addresses;
    private SendCashPanel sendPanel;

    private HushWalletFrame(final StartupProgressDialog progressDialog) throws IOException, InterruptedException, HushCommandLineBridge.WalletCallException {
        super("HUSH Wallet v0.71.1 (beta)");

        if (progressDialog != null) {
            progressDialog.setProgressText("Starting GUI wallet...");
        }

        final ClassLoader cl = this.getClass().getClassLoader();

        this.setIconImage(new ImageIcon(cl.getResource("images/hush-logo-sm.png")).getImage());

        Container contentPane = this.getContentPane();

        errorReporter = new StatusUpdateErrorReporter(this);
        final HushDaemonObserver installationObserver = new HushDaemonObserver(OSUtil.getProgramDirectory());
        final HushCommandLineBridge clientCaller = new HushCommandLineBridge(OSUtil.getProgramDirectory());

        // Build content
        final JTabbedPane tabs = new JTabbedPane();
        Font oldTabFont = tabs.getFont();
        Font newTabFont = new Font(oldTabFont.getName(), Font.BOLD | Font.ITALIC, oldTabFont.getSize() * 57 / 50);
        tabs.setFont(newTabFont);
        tabs.addTab("Overview ",
                new ImageIcon(cl.getResource("images/icon-overview.png")),
                dashboard = new DashboardPanel(this, installationObserver, clientCaller, errorReporter));
        tabs.addTab("Own addresses ",
                new ImageIcon(cl.getResource("images/icon-own-addresses.png")),
                addresses = new AddressesPanel(this, clientCaller, errorReporter));
        tabs.addTab("Send cash ",
                new ImageIcon(cl.getResource("images/icon-send.png")),
                sendPanel = new SendCashPanel(clientCaller, errorReporter));
        tabs.addTab("Address book ",
                new ImageIcon(cl.getResource("images/icon-address-book.png")),
                new AddressBookPanel(sendPanel, tabs));
        contentPane.add(tabs);

        this.walletOps = new WalletOperations(
                this, tabs, dashboard, addresses, sendPanel, clientCaller, errorReporter);

        this.setSize(new Dimension(870, 427));

        // Build menu
        JMenuBar mb = new JMenuBar();
        JMenu file = new JMenu("Main");
        file.setMnemonic(KeyEvent.VK_M);
        int accelaratorKeyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        final JMenuItem menuItemAbout = new JMenuItem("About...", KeyEvent.VK_T);
        file.add(menuItemAbout);
        menuItemAbout.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, accelaratorKeyMask));
        file.addSeparator();
        final JMenuItem menuItemExit = new JMenuItem("Quit", KeyEvent.VK_Q);
        file.add(menuItemExit);
        menuItemExit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, accelaratorKeyMask));
        mb.add(file);

        JMenu wallet = new JMenu("Wallet");
        wallet.setMnemonic(KeyEvent.VK_W);
        final JMenuItem menuItemBackup = new JMenuItem("Backup...", KeyEvent.VK_B);
        wallet.add(menuItemBackup);
        menuItemBackup.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B, accelaratorKeyMask));
        final JMenuItem menuItemEncrypt = new JMenuItem("Encrypt...", KeyEvent.VK_E);
        wallet.add(menuItemEncrypt);
        menuItemEncrypt.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, accelaratorKeyMask));
        final JMenuItem menuItemExportKeys = new JMenuItem("Export private keys...", KeyEvent.VK_K);
        wallet.add(menuItemExportKeys);
        menuItemExportKeys.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_K, accelaratorKeyMask));
        final JMenuItem menuItemImportKeys = new JMenuItem("Import private keys...", KeyEvent.VK_I);
        wallet.add(menuItemImportKeys);
        menuItemImportKeys.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, accelaratorKeyMask));
        final JMenuItem menuItemShowPrivateKey = new JMenuItem("Show private key...", KeyEvent.VK_P);
        wallet.add(menuItemShowPrivateKey);
        menuItemShowPrivateKey.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, accelaratorKeyMask));
        final JMenuItem menuItemImportOnePrivateKey = new JMenuItem("Import one private key...", KeyEvent.VK_N);
        wallet.add(menuItemImportOnePrivateKey);
        menuItemImportOnePrivateKey.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, accelaratorKeyMask));
        mb.add(wallet);

        // Some day the extras menu will be populated with less essential functions
        //JMenu extras = new JMenu("Extras");
        //extras.setMnemonic(KeyEvent.VK_ NOT R);
        //extras.add(menuItemAddressBook = new JMenuItem("Address book...", KeyEvent.VK_D));
        //menuItemAddressBook.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, accelaratorKeyMask));        
        //mb.add(extras);

        // TODO: Temporarily disable encryption until further notice - Oct 24 2016
        menuItemEncrypt.setEnabled(false);

        this.setJMenuBar(mb);

        // Add listeners etc.
        menuItemExit.addActionListener(actionEvent -> HushWalletFrame.this.exitProgram());

        menuItemAbout.addActionListener(
                actionEvent -> {
                    AboutDialog ad = new AboutDialog(HushWalletFrame.this);
                    ad.setVisible(true);
                }
                                       );
        menuItemBackup.addActionListener(actionEvent -> HushWalletFrame.this.walletOps.backupWallet());
        menuItemEncrypt.addActionListener(actionEvent -> HushWalletFrame.this.walletOps.encryptWallet());
        menuItemExportKeys.addActionListener(actionEvent -> HushWalletFrame.this.walletOps.exportWalletPrivateKeys());
        menuItemImportKeys.addActionListener(actionEvent -> HushWalletFrame.this.walletOps.importWalletPrivateKeys());
        menuItemShowPrivateKey.addActionListener(actionEvent -> HushWalletFrame.this.walletOps.showPrivateKey());
        menuItemImportOnePrivateKey.addActionListener(actionEvent -> HushWalletFrame.this.walletOps.importSinglePrivateKey());

        // Close operation
        this.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                HushWalletFrame.this.exitProgram();
            }
        });

        // Show initial message
        SwingUtilities.invokeLater(() -> {
            try {
                final String userDir = OSUtil.getSettingsDirectory();
                final File warningFlagFile = new File(userDir + File.separator + "initialInfoShown.flag");
                if (warningFlagFile.exists()) {
                    return;
                } else {
                    warningFlagFile.createNewFile();
                }

            } catch (IOException e) {
                /* TODO: report exceptions to the user */
                e.printStackTrace();
            }

            JOptionPane.showMessageDialog(
                    HushWalletFrame.this.getRootPane().getParent(),
                    "The HUSH GUI Wallet is currently considered experimental. Use of this software\n" +
                            "comes at your own risk! Be sure to read the list of known issues and limitations\n" +
                            "at this page: https://github.com/MyHush/hush-swing-wallet-ui\n\n" +
                            "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR\n" +
                            "IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,\n" +
                            "FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE\n" +
                            "AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER\n" +
                            "LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,\n" +
                            "OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN\n" +
                            "THE SOFTWARE.\n\n" +
                            "(This message will be shown only once)",
                    "Disclaimer", JOptionPane.INFORMATION_MESSAGE
                                         );
        });

        // Finally dispose of the progress dialog
        if (progressDialog != null) {
            progressDialog.doDispose();
        }
    }

    public static void main(String argv[]) throws IOException {
        try {
            OSUtil.OS_TYPE os = OSUtil.getOSType();

            // On Windows/Mac we log to a file only! - users typically do not use consoles
            if (os == OSUtil.OS_TYPE.WINDOWS || os == OSUtil.OS_TYPE.MAC_OS) {
                redirectLoggingToFile();
            }
            if (os != OSUtil.OS_TYPE.WINDOWS) {
                possiblyCreateHUSHConfigFile(); // this is not run because on Win we have a batch file
                // BRX-TODO: Remove batch file and handle this back in this GUI client again
            }

            System.out.println("Starting HUSH Swing Wallet ...");
            System.out.println("OS: " + System.getProperty("os.name") + " = " + os);
            System.out.println("Current directory: " + new File(".").getCanonicalPath());
            System.out.println("Class path: " + System.getProperty("java.class.path"));
            System.out.println("Environment PATH: " + System.getenv("PATH"));

            // Look and feel settings - for now a custom OS-look and feel is set for Windows,
            // Mac OS will follow later.
            if (os == OSUtil.OS_TYPE.WINDOWS) {
                // Custom Windows L&F and font settings
                UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");

                // This font looks good but on Windows 7 it misses some chars like the stars...
                //FontUIResource font = new FontUIResource("Lucida Sans Unicode", Font.PLAIN, 11);
                //UIManager.put("Table.font", font);
            } else {
                for (LookAndFeelInfo ui : UIManager.getInstalledLookAndFeels()) {
                    System.out.println("Available look and feel: " + ui.getName() + " " + ui.getClassName());
                    if (ui.getName().equals("Nimbus")) {
                        UIManager.setLookAndFeel(ui.getClassName());
                        break;
                    }
                }
            }

            // If hushd is currently not running, do a startup of the daemon as a child process
            // It may be started but not ready - then also show dialog
            HushDaemonObserver initialInstallationObserver =
                    new HushDaemonObserver(OSUtil.getProgramDirectory());
            DaemonInfo hushdInfo = initialInstallationObserver.getDaemonInfo();
            initialInstallationObserver = null;

            HushCommandLineBridge initialClientCaller = new HushCommandLineBridge(OSUtil.getProgramDirectory());
            boolean daemonStartInProgress = false;
            try {
                if (hushdInfo.status == DAEMON_STATUS.RUNNING) {
                    HushCommandLineBridge.NetworkAndBlockchainInfo info = initialClientCaller.getNetworkAndBlockchainInfo();
                    // If more than 20 minutes behind in the blockchain - startup in progress
                    if ((System.currentTimeMillis() - info.lastBlockDate.getTime()) > (20 * 60 * 1000)) {
                        System.out.println("Current blockchain synchronization date is" +
                                                   new Date(info.lastBlockDate.getTime()));
                        daemonStartInProgress = true;
                    }
                }
            } catch (HushCommandLineBridge.WalletCallException wce) {
                if ((wce.getMessage().contains("{\"code\":-28")) || // Started but not ready
                            (wce.getMessage().contains("error code: -28"))) {
                    System.out.println("hushd is currently starting...");
                    daemonStartInProgress = true;
                }
            }

            StartupProgressDialog startupBar = null;
            if ((hushdInfo.status != DAEMON_STATUS.RUNNING) || (daemonStartInProgress)) {
                System.out.println(
                        "hushd is not runing at the moment or has not started/synchronized 100% - showing splash...");
                startupBar = new StartupProgressDialog(initialClientCaller);
                startupBar.setVisible(true);
                startupBar.waitForStartup();
            }
            initialClientCaller = null;

            // Main GUI is created here
            HushWalletFrame ui = new HushWalletFrame(startupBar);
            ui.setVisible(true);

        } catch (InstallationDetectionException ide) {
            ide.printStackTrace();
            JOptionPane.showMessageDialog(
                    null,
                    "This program was started in directory: " + OSUtil.getProgramDirectory() + "\n" +
                            ide.getMessage() + "\n" +
                            "See the console output for more detailed error information!",
                    "Installation error",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        } catch (HushCommandLineBridge.WalletCallException wce) {
            wce.printStackTrace();

            if ((wce.getMessage().contains("{\"code\":-28,\"message\"")) ||
                        (wce.getMessage().contains("error code: -28"))) {
                JOptionPane.showMessageDialog(
                        null,
                        "It appears that hushd has been started but is not ready to accept wallet\n" +
                                "connections. It is still loading the wallet and blockchain. Please try to \n" +
                                "start the GUI wallet later...",
                        "Wallet communication error",
                        JOptionPane.ERROR_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(
                        null,
                        "There was a problem communicating with the HUSH daemon/wallet. \n" +
                                "Please ensure that the HUSH server hushd is started (e.g. via \n" +
                                "command  \"hushd --daemon\"). Error message is: \n" +
                                wce.getMessage() +
                                "See the console output for more detailed error information!",
                        "Wallet communication error",
                        JOptionPane.ERROR_MESSAGE);
            }

            System.exit(2);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(
                    null,
                    "A general unexpected critical error has occurred: \n" + e.getMessage() + "\n" +
                            "See the console output for more detailed error information!",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(3);
        }
    }

    private static void redirectLoggingToFile()
            throws IOException {
        // Initialize log to a file
        String settingsDir = OSUtil.getSettingsDirectory();
        final Date today = new Date();
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(today);
        System.out.println(calendar.get(Calendar.YEAR));
        String logFile = settingsDir + File.separator +
                                 "HUSHWallet_" +
                                 calendar.get(Calendar.YEAR) + "_" + (calendar.get(Calendar.MONTH) + 1) + "_" +
                                 "debug.log";
        PrintStream fileOut = new PrintStream(new FileOutputStream(logFile, true));

        fileOut.println("=================================================================================");
        fileOut.println("= New log started at: " + today.toString());
        fileOut.println("=================================================================================");
        fileOut.println("");

        System.setOut(fileOut);
        System.setErr(fileOut);
    }

    private static void possiblyCreateHUSHConfigFile()
            throws IOException {
        String blockchainDir = OSUtil.getBlockchainDirectory();
        File dir = new File(blockchainDir);

        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                System.out.println("ERROR: Could not create settings directory: " + dir.getCanonicalPath());
                throw new IOException("Could not create settings directory: " + dir.getCanonicalPath());
            }
        }

        File hushConfigFile = new File(dir, "hush.conf");

        if (!hushConfigFile.exists()) {
            System.out.println("HUSH configuration file " + hushConfigFile.getCanonicalPath() +
                                       " does not exist. It will be created with default settings.");

            Random r = new Random(System.currentTimeMillis());

            PrintStream configOut = new PrintStream(new FileOutputStream(hushConfigFile));

            configOut.println("#############################################################################");
            configOut.println("#                         HUSH configuration file                           #");
            configOut.println("#############################################################################");
            configOut.println("# This file has been automatically generated by the HUSH GUI wallet with    #");
            configOut.println("# default settings. It may be further cutsomized by hand only.              #");
            configOut.println("#############################################################################");
            configOut.println("# Creation date: " + new Date().toString());
            configOut.println("#############################################################################");
            configOut.println("");
            configOut.println("# The rpcuser/rpcpassword are used for the local call to hushd");
            configOut.println("rpcuser=User" + Math.abs(r.nextInt()));
            configOut.println("rpcpassword=Pass" + Math.abs(r.nextInt()) + "" +
                                      Math.abs(r.nextInt()) + "" +
                                      Math.abs(r.nextInt()));
            configOut.println("addnode=explorer.myhush.org");
            configOut.println("addnode=stilgar.leto.net");
            configOut.println("addnode=zdash.suprnova.cc");
            configOut.println("addnode=dnsseed.myhush.org");

            configOut.close();
        }
    }

    public void exitProgram() {
        System.out.println("Exiting ...");

        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        this.dashboard.stopThreadsAndTimers();
        this.addresses.stopThreadsAndTimers();
        this.sendPanel.stopThreadsAndTimers();

//        Integer blockchainProgress = this.dashboard.getBlockchainPercentage();
//
//        if ((blockchainProgress != null) && (blockchainProgress >= 100))
//        {
//	        this.dashboard.waitForEndOfThreads(3000);
//	        this.addresses.waitForEndOfThreads(3000);
//	        this.sendPanel.waitForEndOfThreads(3000);
//        }

        HushWalletFrame.this.setVisible(false);
        HushWalletFrame.this.dispose();

        System.exit(0);
    }

}
