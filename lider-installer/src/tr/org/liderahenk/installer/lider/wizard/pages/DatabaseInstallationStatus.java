package tr.org.liderahenk.installer.lider.wizard.pages;

import java.io.IOException;

import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Text;

import tr.org.liderahenk.installer.lider.config.LiderSetupConfig;
import tr.org.liderahenk.installer.lider.i18n.Messages;
import tr.org.liderahenk.installer.lider.utils.PageFlowHelper;
import tr.org.pardus.mys.liderahenksetup.constants.NextPageEventType;
import tr.org.pardus.mys.liderahenksetup.exception.CommandExecutionException;
import tr.org.pardus.mys.liderahenksetup.exception.SSHConnectionException;
import tr.org.pardus.mys.liderahenksetup.utils.PropertyReader;
import tr.org.pardus.mys.liderahenksetup.utils.gui.GUIHelper;
import tr.org.pardus.mys.liderahenksetup.utils.setup.SetupUtils;

/**
 * @author Caner Feyzullahoğlu <caner.feyzullahoglu@agem.com.tr>
 */
public class DatabaseInstallationStatus extends WizardPage
		implements IDatabasePage, ControlNextEvent, InstallationStatusPage {

	private LiderSetupConfig config;

	private ProgressBar progressBar;
	private Text txtLogConsole;

	private NextPageEventType nextPageEventType;

	boolean isInstallationFinished = false;

	boolean canGoBack = false;

	// TODO database parametric
	private final static String CREATE_DATABASE = "mysql -uroot -p{0} -e 'CREATE DATABASE liderdb DEFAULT CHARACTER SET utf8 DEFAULT COLLATE utf8_general_ci'";

	private final static String GRANT_PRIVILEGES = "mysql -uroot -p{0} -e \"GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' IDENTIFIED BY '{1}' WITH GRANT OPTION;\"";

	private final static String REPLACE_BIND_ADDRESS = "sed -i 's/^bind-address/#&/' /etc/mysql/my.cnf";

	public DatabaseInstallationStatus(LiderSetupConfig config) {
		super(DatabaseInstallationStatus.class.getName(), Messages.getString("LIDER_INSTALLATION"), null);
		setDescription("2.4 " + Messages.getString("DATABASE_INSTALLATION"));
		this.config = config;
	}

	@Override
	public void createControl(Composite parent) {
		Composite container = GUIHelper.createComposite(parent, 1);
		setControl(container);

		txtLogConsole = GUIHelper.createText(container, new GridData(GridData.FILL_BOTH),
				SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);

		progressBar = new ProgressBar(container, SWT.SMOOTH | SWT.HORIZONTAL);
		progressBar.setSelection(0);
		progressBar.setMaximum(100);
		GridData progressGd = new GridData(GridData.FILL_HORIZONTAL);
		progressGd.heightHint = 40;
		// progressGd.widthHint = 780;
		progressBar.setLayoutData(progressGd);
	}

	@Override
	public IWizardPage getNextPage() {

		// Start database installation here.
		// To prevent triggering installation again
		// (i.e. when clicked "next" after installation finished),
		// set isInstallationFinished to true when its done.
		if (super.isCurrentPage() && !isInstallationFinished
				&& nextPageEventType == NextPageEventType.CLICK_FROM_PREV_PAGE) {

			final Display display = Display.getCurrent();

			canGoBack = false;

			Runnable runnable = new Runnable() {
				@Override
				public void run() {

					setPageCompleteAsync(isInstallationFinished);

					// Clear text log console and progress bar before starting
					// installation.
					clearLogConsole();
					setProgressBar(0);

					printMessage(Messages.getString("INITIALIZING_INSTALLATION"));
					setProgressBar(10);

					try {
						
						printMessage(Messages.getString("CLEANING_OLD_DATABASES"));
						SetupUtils.executeCommand(config.getDatabaseIp(), config.getDatabaseAccessUsername(),
								config.getDatabaseAccessPasswd(), config.getDatabasePort(),
								config.getDatabaseAccessKeyPath(), config.getDatabaseAccessPassphrase(),
								"sudo rm -rf /var/lib/mysql/");
						printMessage(Messages.getString("SUCCESSFULLY_CLEANED_OLD_DATABASES"));

						printMessage(Messages.getString("UPDATING_PACKAGE_LIST"));
						SetupUtils.executeCommand(config.getDatabaseIp(), config.getDatabaseAccessUsername(),
								config.getDatabaseAccessPasswd(), config.getDatabasePort(),
								config.getDatabaseAccessKeyPath(), config.getDatabaseAccessPassphrase(),
								"sudo apt-get update");
						printMessage(Messages.getString("SUCCESSFULLY_UPDATED_PACKAGE_LIST"));

						printMessage(Messages.getString("INSTALLING_PREREQUISITES"));
						SetupUtils.executeCommand(config.getDatabaseIp(), config.getDatabaseAccessUsername(),
								config.getDatabaseAccessPasswd(), config.getDatabasePort(),
//								config.getDatabaseAccessKeyPath(), config.getDatabaseAccessPassphrase(),
//								"sudo apt-get -y --force-yes install software-properties-common");
								config.getDatabaseAccessKeyPath(), config.getDatabaseAccessPassphrase(),
								"sudo apt-get -y --force-yes install software-properties-common dirmngr");
						printMessage(Messages.getString("SUCCESSFULLY_INSTALLED_PREREQUISITES"));

						printMessage(Messages.getString("ADDING_KEY_SERVER"));
						SetupUtils.executeCommand(config.getDatabaseIp(), config.getDatabaseAccessUsername(),
								config.getDatabaseAccessPasswd(), config.getDatabasePort(),
//								config.getDatabaseAccessKeyPath(), config.getDatabaseAccessPassphrase(),
//								"sudo apt-key adv --recv-keys --keyserver keyserver.ubuntu.com 0xcbcb082a1bb943db");
								config.getDatabaseAccessKeyPath(), config.getDatabaseAccessPassphrase(),
								"sudo apt-key adv --recv-keys --keyserver keyserver.ubuntu.com 0xF1656F24C74CD1D8");

						printMessage(Messages.getString("SUCCESSFULLY_ADDED_KEY_SERVER"));

						printMessage(Messages.getString("ADDING_NEW_REPOSITORY"));
						SetupUtils.executeCommand(config.getDatabaseIp(), config.getDatabaseAccessUsername(),
								config.getDatabaseAccessPasswd(), config.getDatabasePort(),
//								config.getDatabaseAccessKeyPath(), config.getDatabaseAccessPassphrase(),
//								"sudo echo 'deb [arch=amd64,i386] ftp://ftp.ulak.net.tr/pub/MariaDB/repo/10.1/debian jessie main' > /etc/apt/sources.list.d/galera.list");
								config.getDatabaseAccessKeyPath(), config.getDatabaseAccessPassphrase(),
								"sudo echo 'deb [arch=amd64] ftp://ftp.ulak.net.tr/pub/MariaDB/repo/10.2/debian stretch main' > /etc/apt/sources.list.d/galera.list");						
						printMessage(Messages.getString("SUCCESSFULLY_ADDED_NEW_REPOSITORY"));

						printMessage(Messages.getString("UPDATING_PACKAGE_LIST"));
						SetupUtils.executeCommand(config.getDatabaseIp(), config.getDatabaseAccessUsername(),
								config.getDatabaseAccessPasswd(), config.getDatabasePort(),
								config.getDatabaseAccessKeyPath(), config.getDatabaseAccessPassphrase(),
								"sudo apt-get update");
						printMessage(Messages.getString("SUCCESSFULLY_UPDATED_PACKAGE_LIST"));

						printMessage(Messages.getString("SETTING_DEBIAN_FRONTEND_TO_NONINTERACTIVE"));
						SetupUtils.executeCommand(config.getDatabaseIp(), config.getDatabaseAccessUsername(),
								config.getDatabaseAccessPasswd(), config.getDatabasePort(),
								config.getDatabaseAccessKeyPath(), config.getDatabaseAccessPassphrase(),
								"export DEBIAN_FRONTEND='noninteractive'");
						printMessage(Messages.getString("SUCCESSFULLY_SET_DEBIAN_FRONTEND_TO_NONINTERACTIVE"));

						final String[] debconfValues = generateDebconfValues();

						for (String value : debconfValues) {
							String cmd = "debconf-set-selections <<< '{0}'";
							cmd = cmd.replace("{0}", value);
							SetupUtils.executeCommand(config.getDatabaseIp(), config.getDatabaseAccessUsername(),
									config.getDatabaseAccessPasswd(), config.getDatabasePort(),
									config.getDatabaseAccessKeyPath(), config.getDatabaseAccessPassphrase(), cmd);
						}
						
						printMessage(Messages.getString("INSTALLING_MARIADB_SERVER"));
						SetupUtils.executeCommand(config.getDatabaseIp(), config.getDatabaseAccessUsername(),
								config.getDatabaseAccessPasswd(), config.getDatabasePort(),
//								config.getDatabaseAccessKeyPath(), config.getDatabaseAccessPassphrase(),
//								"apt-get -y --force-yes install mariadb-server-10.1");
								config.getDatabaseAccessKeyPath(), config.getDatabaseAccessPassphrase(),
								"apt-get -y --force-yes install mariadb-server-10.2");
						printMessage(Messages.getString("SUCCESSFULLY_INSTALLED_MARIADB_SERVER"));

						// Grant privileges
						printMessage(Messages.getString("GRANTING_PRIVILEGES"));
						SetupUtils.executeCommand(config.getDatabaseIp(), config.getDatabaseAccessUsername(),
								config.getDatabaseAccessPasswd(), config.getDatabasePort(),
								config.getDatabaseAccessKeyPath(), config.getDatabaseAccessPassphrase(),
								GRANT_PRIVILEGES.replace("{0}", config.getDatabaseRootPassword()).replace("{1}",
										config.getDatabaseRootPassword()));

						printMessage(Messages.getString("CREATING_DATABASE"));
						SetupUtils.executeCommand(config.getDatabaseIp(), config.getDatabaseAccessUsername(),
								config.getDatabaseAccessPasswd(), config.getDatabasePort(),
								config.getDatabaseAccessKeyPath(), config.getDatabaseAccessPassphrase(),
								CREATE_DATABASE.replace("{0}", config.getDatabaseRootPassword()));

						printMessage(Messages.getString("DELETING_DEFAULT_USERS"));
						String command = "mysql -uroot -p{0} -e \"DELETE FROM mysql.user WHERE user='';\"";
						command = command.replace("{0}", config.getDatabaseRootPassword());
						SetupUtils.executeCommand(config.getDatabaseIp(), config.getDatabaseAccessUsername(),
								config.getDatabaseAccessPasswd(), config.getDatabasePort(),
								config.getDatabaseAccessKeyPath(), config.getDatabaseAccessPassphrase(), command);

						printMessage(Messages.getString("GRANTING_OTHER_PRIVILEGES"));
						command = "mysql -uroot -p{0} -e \"GRANT ALL ON *.* TO 'root'@'%' IDENTIFIED BY '{1}';\"";
						command = command.replace("{0}", config.getDatabaseRootPassword()).replace("{1}",
								config.getDatabaseRootPassword());
						SetupUtils.executeCommand(config.getDatabaseIp(), config.getDatabaseAccessUsername(),
								config.getDatabaseAccessPasswd(), config.getDatabasePort(),
								config.getDatabaseAccessKeyPath(), config.getDatabaseAccessPassphrase(), command);

						printMessage(Messages.getString("FLUSHING_PRIVILEGES"));
						command = "mysql -uroot -p{0} -e \"FLUSH PRIVILEGES;\"";
						command = command.replace("{0}", config.getDatabaseRootPassword());
						SetupUtils.executeCommand(config.getDatabaseIp(), config.getDatabaseAccessUsername(),
								config.getDatabaseAccessPasswd(), config.getDatabasePort(),
								config.getDatabaseAccessKeyPath(), config.getDatabaseAccessPassphrase(), command);

						// Remove bind-address
						printMessage(Messages.getString("REMOVING_BIND_ADDRESS_PARAMETER"));
						SetupUtils.executeCommand(config.getDatabaseIp(), config.getDatabaseAccessUsername(),
								config.getDatabaseAccessPasswd(), config.getDatabasePort(),
								config.getDatabaseAccessKeyPath(), config.getDatabaseAccessPassphrase(),
								REPLACE_BIND_ADDRESS);

						printMessage(Messages.getString("RESTARTING_MYSQL_SERVICE"));
						SetupUtils.executeCommand(config.getDatabaseIp(), config.getDatabaseAccessUsername(),
								config.getDatabaseAccessPasswd(), config.getDatabasePort(),
								config.getDatabaseAccessKeyPath(), config.getDatabaseAccessPassphrase(),
								"service mysql restart");

						printMessage(Messages.getString("MARIADB_INSTALLATION_SUCCESSFULLY_COMPLETED"));
						
						setProgressBar(90);

						isInstallationFinished = true;

					} catch (CommandExecutionException e) {
						isInstallationFinished = false;
						// If any error occured user should be able to go
						// back and change selections etc.
						canGoBack = true;
						printMessage(Messages.getString("ERROR_OCCURED", e.getMessage()));
						e.printStackTrace();
					} catch (SSHConnectionException e) {
						isInstallationFinished = false;
						// If any error occured user should be able to go
						// back and change selections etc.
						canGoBack = true;
						printMessage(Messages.getString("ERROR_OCCURED", e.getMessage()));
						e.printStackTrace();
					}

					setProgressBar(100);

					config.setInstallationFinished(isInstallationFinished);

					setPageCompleteAsync(isInstallationFinished);

					if (!isInstallationFinished) {
						try {
							openDownloadUrl();
						} catch (Exception e) {
							e.printStackTrace();
							txtLogConsole.setText((txtLogConsole.getText() != null && !txtLogConsole.getText().isEmpty()
									? txtLogConsole.getText() + "\n" : "")
									+ Messages.getString("CANNOT_OPEN_BROWSER_PLEASE_GO_TO") + "\n"
									+ PropertyReader.property("troubleshooting.url"));
						}
					}

				}

				/**
				 * Prints log message to the log console widget
				 * 
				 * @param message
				 */
				private void printMessage(final String message) {
					display.asyncExec(new Runnable() {
						@Override
						public void run() {
							try {
								Thread.sleep(500);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							txtLogConsole.setText((txtLogConsole.getText() != null && !txtLogConsole.getText().isEmpty()
									? txtLogConsole.getText() + "\n" : "") + message);
						}
					});
				}

				/**
				 * Clears log console by set its content to empty string.
				 */
				private void clearLogConsole() {
					display.asyncExec(new Runnable() {
						@Override
						public void run() {
							try {
								Thread.sleep(500);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							txtLogConsole.setText("");
						}
					});
				}

				/**
				 * Sets progress bar selection
				 * 
				 * @param selection
				 */
				private void setProgressBar(final int selection) {
					display.asyncExec(new Runnable() {
						@Override
						public void run() {
							progressBar.setSelection(selection);
						}
					});
				}

				/**
				 * Sets page complete status asynchronously.
				 * 
				 * @param isComplete
				 */
				private void setPageCompleteAsync(final boolean isComplete) {
					display.asyncExec(new Runnable() {
						@Override
						public void run() {
							setPageComplete(isComplete);
						}
					});
				}
			};

			Thread thread = new Thread(runnable);
			thread.start();
		}

		// Select next page.
		return PageFlowHelper.selectNextPage(config, this);
	}

	private void openDownloadUrl() throws IOException {
		Runtime.getRuntime().exec("xdg-open " + PropertyReader.property("troubleshooting.url"));
	}

	/**
	 * Generates debconf values for database root password
	 * 
	 * @return
	 */
	public String[] generateDebconfValues() {
		String debconfPwd = PropertyReader.property("database.cluster.debconf.password") + " "
				+ config.getDatabaseRootPassword();
		String debconfPwdAgain = PropertyReader.property("database.cluster.debconf.password.again") + " "
				+ config.getDatabaseRootPassword();
		return new String[] { debconfPwd, debconfPwdAgain };
	}

	@Override
	public IWizardPage getPreviousPage() {
		// Do not allow to go back from this page if installation completed
		// successfully.
		if (canGoBack) {
			return super.getPreviousPage();
		} else {
			return null;
		}
	}

	@Override
	public NextPageEventType getNextPageEventType() {
		return this.nextPageEventType;
	}

	@Override
	public void setNextPageEventType(NextPageEventType nextPageEventType) {
		this.nextPageEventType = nextPageEventType;
	}

}
