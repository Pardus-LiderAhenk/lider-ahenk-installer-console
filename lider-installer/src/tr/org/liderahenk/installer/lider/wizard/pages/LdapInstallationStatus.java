package tr.org.liderahenk.installer.lider.wizard.pages;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.internal.about.ConfigurationLogDefaultSection;

import tr.org.liderahenk.installer.lider.config.LiderSetupConfig;
import tr.org.liderahenk.installer.lider.i18n.Messages;
import tr.org.liderahenk.installer.lider.utils.PageFlowHelper;
import tr.org.pardus.mys.liderahenksetup.constants.InstallMethod;
import tr.org.pardus.mys.liderahenksetup.constants.NextPageEventType;
import tr.org.pardus.mys.liderahenksetup.constants.PackageInstaller;
import tr.org.pardus.mys.liderahenksetup.exception.CommandExecutionException;
import tr.org.pardus.mys.liderahenksetup.exception.SSHConnectionException;
import tr.org.pardus.mys.liderahenksetup.utils.LiderAhenkUtils;
import tr.org.pardus.mys.liderahenksetup.utils.PropertyReader;
import tr.org.pardus.mys.liderahenksetup.utils.gui.GUIHelper;
import tr.org.pardus.mys.liderahenksetup.utils.setup.SetupUtils;

/**
 * @author <a href="mailto:caner.feyzullahoglu@agem.com.tr">Caner Feyzullahoglu</a>
 * 
 */
public class LdapInstallationStatus extends WizardPage implements ILdapPage, InstallationStatusPage, ControlNextEvent {

	private LiderSetupConfig config;

	private ProgressBar progressBar;
	private Text txtLogConsole;

	private NextPageEventType nextPageEventType;

	boolean isInstallationFinished = false;

	boolean canGoBack = false;

	public LdapInstallationStatus(LiderSetupConfig config) {
		super(LdapInstallationStatus.class.getName(), Messages.getString("LIDER_INSTALLATION"), null);
		setDescription("3.4 " + Messages.getString("LDAP_INSTALLATION"));
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
		// Start LDAP installation here.
		// To prevent triggering installation again
		// (i.e. when clicked "next" after installation finished),
		// set isInstallationFinished to true when its done.
		if (super.isCurrentPage() && !isInstallationFinished
				&& nextPageEventType == NextPageEventType.CLICK_FROM_PREV_PAGE) {

			final Display display = Display.getCurrent();
			Runnable runnable = new Runnable() {
				@Override
				public void run() {

					setPageCompleteAsync(isInstallationFinished);
					
					printMessage(Messages.getString("INITIALIZING_INSTALLATION"));
					setProgressBar(10);

					printMessage(Messages.getString("SETTING_UP_DEBCONF_VALUES"));
					final String[] debconfValues = generateDebconfValues();
					setProgressBar(20);


					if (config.getLdapInstallMethod() == InstallMethod.PROVIDED_DEB) {
						File deb = new File(config.getLdapDebFileName());
						try {
							printMessage(Messages.getString("INSTALLING_SLAPD_PACKAGE"));
							SetupUtils.installPackageNonInteractively(config.getLdapIp(),
									config.getLdapAccessUsername(), config.getLdapAccessPasswd(), config.getLdapPort(),
									config.getLdapAccessKeyPath(), config.getLdapAccessPassphrase(), deb, debconfValues,
									PackageInstaller.GDEBI);
							setProgressBar(90);
							isInstallationFinished = true;
							printMessage(Messages.getString("SUCCESSFULLY_INSTALLED_PACKAGE", deb.getName()));
						} catch (Exception e) {
							isInstallationFinished = false;
							canGoBack = true;
							printMessage(Messages.getString("ERROR_OCCURED", e.getMessage()));
							e.printStackTrace();
						}
					} else if (config.getLdapInstallMethod() == InstallMethod.WGET) {
						try {
							printMessage(Messages.getString("DOWNLOADING_DEB_PACKAGE_FROM", config.getLdapDownloadUrl()));

							SetupUtils.downloadPackage(config.getLdapIp(), config.getLdapAccessUsername(),
									config.getLdapAccessPasswd(), config.getLdapPort(), config.getLdapAccessKeyPath(),
									config.getLdapAccessPassphrase(), "openldap.deb",
									config.getLdapDownloadUrl());

							setProgressBar(30);

							printMessage(Messages.getString("SUCCESSFULLY_DOWNLOADED_PACKAGE"));

							printMessage(Messages.getString("INSTALLING_DOWNLOADED_PACKAGE"));
							SetupUtils.installDownloadedPackage(config.getLdapIp(), config.getLdapAccessUsername(),
									config.getLdapAccessPasswd(), config.getLdapPort(), config.getLdapAccessKeyPath(),
									config.getLdapAccessPassphrase(), "openldap.deb",
									PackageInstaller.GDEBI);
							printMessage(Messages.getString("SUCCESSFULLY_INSTALLED_DOWNLOADED_PACKAGE"));

						} catch (CommandExecutionException e) {
							isInstallationFinished = false;
							canGoBack = true;
							printMessage(Messages.getString("ERROR_OCCURED", e.getMessage()));
							e.printStackTrace();
						} catch (SSHConnectionException e) {
							isInstallationFinished = false;
							canGoBack = true;
							printMessage(Messages.getString("ERROR_OCCURED", e.getMessage()));
							e.printStackTrace();
						}

					} else {
						isInstallationFinished = false;
						printMessage(Messages.getString("INVALID_INSTALLATION_METHOD"));
					}

					File ldapConfigFile;
					try {
						ldapConfigFile = new File(config.getLdapAbsPathConfFile());

						InputStream inputStream = this.getClass().getClassLoader()
								.getResourceAsStream("liderahenk.ldif");
						File liderAhenkLdifFile = LiderAhenkUtils.streamToFile(inputStream, "liderahenk.ldif");
						
						// Delete previous databases
						printMessage(Messages.getString("DELETING_PREVIOUS_DATABASES"));
						SetupUtils.executeCommand(config.getLdapIp(), config.getLdapAccessUsername(),
								config.getLdapAccessPasswd(), config.getLdapPort(), config.getLdapAccessKeyPath(),
								config.getLdapAccessPassphrase(), "rm -rf /var/ldaps/");
						printMessage(Messages.getString("SUCCESSFULLY_DELETED_PREVIOUS_DATABASES"));

						// Send liderahenk.ldif
						printMessage(Messages.getString("SENDING_LIDER_AHENK_LDIF"));
						SetupUtils.copyFile(config.getLdapIp(), config.getLdapAccessUsername(),
								config.getLdapAccessPasswd(), config.getLdapPort(), config.getLdapAccessKeyPath(),
								config.getLdapAccessPassphrase(), liderAhenkLdifFile, "/tmp/");
						printMessage(Messages.getString("SUCCESSFULLY_SENT_LIDER_AHENK_LDIF"));

						// Send LDAP config script
						printMessage(Messages.getString("SENDING_LDAP_CONFIG"));
						SetupUtils.copyFile(config.getLdapIp(), config.getLdapAccessUsername(),
								config.getLdapAccessPasswd(), config.getLdapPort(), config.getLdapAccessKeyPath(),
								config.getLdapAccessPassphrase(), ldapConfigFile, "/tmp/");
						printMessage(Messages.getString("SUCCESSFULLY_SENT_LDAP_CONFIG"));

						// Maket it executable
						printMessage(Messages.getString("MODIFYING_LDAP_CONFIG"));
						SetupUtils.executeCommand(config.getLdapIp(), config.getLdapAccessUsername(),
								config.getLdapAccessPasswd(), config.getLdapPort(), config.getLdapAccessKeyPath(),
								config.getLdapAccessPassphrase(), "chmod +x /tmp/" + ldapConfigFile.getName());
						printMessage(Messages.getString("SUCCESSFULLY_MODIFIED_LDAP_CONFIG"));
						
						// Install ldap-utils
						printMessage(Messages.getString("INSTALLING_LDAP_UTILS"));
						SetupUtils.installPackage(config.getLdapIp(), config.getLdapAccessUsername(),
								config.getLdapAccessPasswd(), config.getLdapPort(), config.getLdapAccessKeyPath(),
								config.getLdapAccessPassphrase(), "ldap-utils", null);
						printMessage(Messages.getString("SUCCESSFULLY_INSTALLED_LDAP_UTILS"));

						// Run LDAP config script
						printMessage(Messages.getString("EXECUTING_LDAP_CONFIG_SCRIPT"));
						SetupUtils.executeCommand(config.getLdapIp(), config.getLdapAccessUsername(),
								config.getLdapAccessPasswd(), config.getLdapPort(), config.getLdapAccessKeyPath(),
								config.getLdapAccessPassphrase(), "/tmp/" + ldapConfigFile.getName());
						printMessage(Messages.getString("SUCCESSFULLY_EXECUTED_LDAP_CONFIG_SCRIPT"));
						

						printMessage(Messages.getString("OPENLDAP_INSTALLATION_COMPLETED_SUCCESSFULLY"));
						
						isInstallationFinished = true;
					} catch (SSHConnectionException e) {
						isInstallationFinished = false;
						canGoBack = true;
						printMessage(Messages.getString("ERROR_OCCURED", e.getMessage()));
						e.printStackTrace();
					} catch (CommandExecutionException e) {
						isInstallationFinished = false;
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
		String debconfPwd = PropertyReader.property("ldap.debconf.password1") + " " + config.getLdapRootPassword();
		String debconfPwdAgain = PropertyReader.property("ldap.debconf.password2") + " " + config.getLdapRootPassword();
		String debconfAdminPwd = PropertyReader.property("ldap.debconf.adminpw") + " " + config.getLdapRootPassword();
		String debconfGeneratedPwd = PropertyReader.property("ldap.debconf.generated.password") + " "
				+ config.getLdapRootPassword();
//		String debconfLdapDomain = PropertyReader.property("ldap.debconf.domain") + " " + config.getLdapBaseDn();
//		
	    String dn = config.getLdapBaseDn().replace("dc=", "");
	    String ndn = dn.replace("dc=", "");
	    String LdapBaseDn = ndn.replace(",", ".");
	    System.out.println(LdapBaseDn);
		
//		text = config.getLdapBaseDn().replace(, text);
		String debconfLdapDomain = PropertyReader.property("ldap.debconf.domain") + " " + LdapBaseDn;
		String debconfLdapOrganization = PropertyReader.property("ldap.debconf.organization") + " " + config.getLdapOrgName();
//		String debconfAcl = PropertyReader.property("ldap.debconf.acl");
//		String debconfPurgeDB = PropertyReader.property("ldap.debconf.purgedb");
//		String debconfLdapPpolicy = PropertyReader.property("ldap.debconf.ppolicy");
//		String debconfLdapConfig = PropertyReader.property("ldap.debconf.config");
//		String debconfMoveDB = PropertyReader.property("ldap.debconf.movedb");
//		String debconfSelectDB = PropertyReader.property("ldap.debconf.selectdb");
//		String debconfBackupDB = PropertyReader.property("ldap.debconf.backupdb");
//		String debconfLdapConf = PropertyReader.property("ldap.debconf.conf");
//		String debconfDbDump = PropertyReader.property("ldap.debconf.dbdump");
//		String debconfPwdMatch = PropertyReader.property("ldap.debconf.pwdmatch");
//		
		
		return new String[] { debconfPwd, debconfPwdAgain, debconfAdminPwd, debconfGeneratedPwd, debconfLdapDomain, debconfLdapOrganization };
//				, debconfAcl, debconfPurgeDB, debconfLdapConfig, debconfMoveDB,
//				debconfBackupDB, debconfLdapConf, debconfDbDump, debconfPwdMatch};
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
