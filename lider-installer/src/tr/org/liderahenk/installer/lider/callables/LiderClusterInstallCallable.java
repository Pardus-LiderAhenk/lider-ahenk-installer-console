package tr.org.liderahenk.installer.lider.callables;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;

import tr.org.liderahenk.installer.lider.config.LiderSetupConfig;
import tr.org.liderahenk.installer.lider.i18n.Messages;
import tr.org.pardus.mys.liderahenksetup.exception.CommandExecutionException;
import tr.org.pardus.mys.liderahenksetup.exception.SSHConnectionException;
import tr.org.pardus.mys.liderahenksetup.utils.PropertyReader;
import tr.org.pardus.mys.liderahenksetup.utils.setup.SSHManager;
import tr.org.pardus.mys.liderahenksetup.utils.setup.SetupUtils;

/**
 * @author <a href="mailto:caner.feyzullahoglu@agem.com.tr">Caner Feyzullahoglu</a>
 * 
 */
public class LiderClusterInstallCallable implements Callable<Boolean> {

	private static final Logger logger = Logger.getLogger(LiderClusterInstallCallable.class.getName());

	private String nodeIp;
	private String nodeRootPwd;
	private String nodeXmppResource;

	private Display display;
	private LiderSetupConfig config;
	private Text txtLogConsole;
	private boolean firstNode;

	public LiderClusterInstallCallable(String nodeIp, String nodeRootPwd, String nodeXmppResource, Display display, LiderSetupConfig config,
			Text txtLogConsole, boolean firstNode) {
		super();
		this.nodeIp = nodeIp;
		this.nodeRootPwd = nodeRootPwd;
		this.display = display;
		this.config = config;
		this.txtLogConsole = txtLogConsole;
		this.nodeXmppResource = nodeXmppResource;
		this.firstNode = firstNode;
	}

	@Override
	public Boolean call() throws Exception {

		return installStandAloneKaraf();
	}

	private Boolean installStandAloneKaraf() {

		SSHManager manager = null;

		boolean successfullSetup = false;
		try {
			// Check SSH connection
			try {
				printMessage(Messages.getString("CHECKING_CONNECTION_TO") + " " + nodeIp, display);

				manager = new SSHManager(nodeIp, "root", nodeRootPwd, config.getXmppPort(),
						config.getXmppAccessKeyPath(), config.getXmppAccessPassphrase());
				manager.connect();

				printMessage(Messages.getString("CONNECTION_ESTABLISHED_TO") + " " + nodeIp, display);
				logger.log(Level.INFO, "Connection established to: {0} with username: {1}",
						new Object[] { nodeIp, "root" });

			} catch (SSHConnectionException e) {
				printMessage(Messages.getString("COULD_NOT_CONNECT_TO_NODE") + " " + nodeIp, display);
				printMessage(Messages.getString("CHECK_SSH_ROOT_PERMISSONS_OF" + " " + nodeIp), display);
				printMessage(Messages.getString("EXCEPTION_MESSAGE") + " " + e.getMessage() + " at " + nodeIp, display);
				e.printStackTrace();
				logger.log(Level.SEVERE, e.getMessage());
				throw new Exception();
			}

			// Send tar file of Karaf
			try {

				InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("lider-cluster.tar.gz");
				File karafTar = SetupUtils.streamToFile(inputStream, "lider.tar.gz");

				printMessage(Messages.getString("SENDING_TAR_FILE_TO") + " " + nodeIp, display);
				manager.copyFileToRemote(karafTar, "/tmp/", false);
				printMessage(Messages.getString("SUCCESSFULLY_SENT_TAR_FILE_TO") + " " + nodeIp, display);
				logger.log(Level.INFO, "Successfully sent Karaf tar to: {0}", new Object[] { nodeIp });

			} catch (CommandExecutionException e) {
				printMessage(Messages.getString("EXCEPTION_RAISED_WHILE_SENDING_TAR_FILE_TO") + " " + nodeIp, display);
				printMessage(Messages.getString("EXCEPTION_MESSAGE") + " " + e.getMessage() + " at " + nodeIp, display);
				logger.log(Level.SEVERE, e.getMessage());
				e.printStackTrace();
				throw new Exception();
			}

			// Extract tar
			try {
				printMessage(Messages.getString("EXTRACTING_TAR_FILE_AT") + " " + nodeIp, display);
				manager.execCommand("tar -xzvf {0} --directory {1}", new Object[] { "/tmp/lider.tar.gz", "/opt/" });
				printMessage(Messages.getString("SUCCESSFULLY_EXTRACTED_TAR_FILE_AT") + " " + nodeIp, display);
				logger.log(Level.INFO, "Successfully tar file at: {0}", new Object[] { nodeIp });

			} catch (CommandExecutionException e) {
				printMessage(Messages.getString("EXCEPTION_RAISED_WHILE_EXTRACTING_TAR_FILE_AT") + " " + nodeIp,
						display);
				printMessage(Messages.getString("EXCEPTION_MESSAGE") + " " + e.getMessage() + " at " + nodeIp, display);
				logger.log(Level.SEVERE, e.getMessage());
				e.printStackTrace();
				throw new Exception();
			}

			// Send tr.org.liderahenk.cfg
			try {

				printMessage(Messages.getString("CREATING_CFG_FILE"), display);
				String liderCfg = readFile("tr.org.liderahenk.cfg");
				Map<String, String> map = new HashMap<>();
				map.put("#LDAPSERVER", config.getLiderLdapServerAddress());
				map.put("#LDAPPORT", config.getLiderLdapPort().toString());
				map.put("#LDAPUSERNAME", config.getLiderLdapAdminUser());
				map.put("#LDAPPASSWORD", config.getLiderLdapAdminPwd());
				map.put("#LDAPROOTDN", config.getLdapBaseDn());
				map.put("#LDAP_SSL", config.getLiderLdapUseSsl());

				map.put("#XMPPHOST", config.getLiderXmppAddress());
				map.put("#XMPPPORT", config.getLiderXmppPort().toString());
				map.put("#XMPPUSERNAME", config.getLiderXmppLiderUser());
				map.put("#XMPPPASSWORD", config.getLiderXmppLiderPwd());
				map.put("#XMPPRESOURCE", nodeXmppResource);
				map.put("#XMPPSERVICENAME", config.getLiderXmppServiceName());
				map.put("#XMPPMAXRETRY", config.getLiderXmppMaxTrials());
				map.put("#XMPPREPLAYTIMEOUT", config.getLiderXmppPacketTimeout());
				map.put("#XMPPPINGTIMEOUT", config.getLiderXmppPingTimeout());
				map.put("#XMPP_SSL", config.getLiderXmppUseSsl());

				map.put("#AGENTLDAPBASEDN", config.getLiderAgentLdapBaseDn());
				map.put("#AGENTLDAPIDATTR", config.getLiderAgentLdapIdAttribute());
				map.put("#AGENTLDAPJIDATTR", config.getLiderAgentLdapJidAttribute());
				map.put("#AGENTLDAPOBJECTCLASSES", config.getLiderAgentLdapClasses());

				map.put("#USERLDAPBASEDN", config.getLiderUserLdapBaseDn());
				map.put("#USERLDAPUIDATTR", config.getLiderUserLdapIdAttribute());
				map.put("#USERLDAPPRIVILEGEATTR", config.getLiderUserLdapPrivilegeAttribute());
				map.put("#USERLDAPOBJECTCLASSES", config.getLiderUserLdapClasses());
				map.put("#GROUPLDAPOBJECTCLASSES", config.getLiderUserGroupLdapClasses());

				if (firstNode) {
					map.put("#CHECK_FUTURE_TASK", "true");
					map.put("#ALARM_CHECK_REPORT", "true");
				} else {
					map.put("#CHECK_FUTURE_TASK", "false");
					map.put("#ALARM_CHECK_REPORT", "false");
				}
				
				
				map.put("#FILE_SERVER_PROTOCOL", config.getLiderFileServerProtocol());
				map.put("#FILE_SERVER_HOST", config.getLiderFileServerHost());
				map.put("#FILE_SERVER_PORT", config.getLiderFileServerPort());
				map.put("#FILE_SERVER_USERNAME", config.getLiderFileServerUsername());
				map.put("#FILE_SERVER_PWD", config.getLiderFileServerPwd());
				map.put("#FILE_SERVER_PLUGIN_PATH", config.getLiderFileServerPluginPath());
				map.put("#FILE_SERVER_AGREEMENT_PATH", config.getLiderFileServerAgreementPath());
				map.put("#FILE_SERVER_AGENT_FILE_PATH", config.getLiderFileServerAgentFilePath());

				liderCfg = SetupUtils.replace(map, liderCfg);
				File liderCfgFile = SetupUtils.writeToFile(liderCfg, "tr.org.liderahenk.cfg");
				printMessage(Messages.getString("SUCCESSFULLY_CREATED_CFG_FILE"), display);

				printMessage(Messages.getString("SENDING_CFG_TO") + " " + nodeIp, display);
				manager.copyFileToRemote(liderCfgFile,
						"/opt/" + PropertyReader.property("lider.package.name") + "/etc/", false);
				printMessage(Messages.getString("SUCCESSFULLY_SENT_CFG_TO") + " " + nodeIp, display);
				logger.log(Level.INFO, "Successfully sent tr.org.liderahenk.cfg to: {0}", new Object[] { nodeIp });
			} catch (CommandExecutionException e) {
				printMessage(Messages.getString("EXCEPTION_RAISED_WHILE_SENDING_CFG_TO") + " " + nodeIp, display);
				printMessage(Messages.getString("EXCEPTION_MESSAGE") + " " + e.getMessage() + " at " + nodeIp, display);
				logger.log(Level.SEVERE, e.getMessage());
				e.printStackTrace();
				throw new Exception();
			}

			// Send tr.org.liderahenk.datasource.cfg
			try {

				printMessage(Messages.getString("CREATING_DATASOURCE_CFG_FILE"), display);
				String liderDatasourceCfg = readFile("tr.org.liderahenk.datasource.cfg");
				Map<String, String> map = new HashMap<>();
				map.put("#DBADDRESS", config.getLiderDbAddress());
				map.put("#DBDATABASE", config.getLiderDbName());
				map.put("#DBUSERNAME", config.getLiderDbUsername());
				map.put("#DBPASSWORD", config.getLiderDbPwd());

				liderDatasourceCfg = SetupUtils.replace(map, liderDatasourceCfg);
				File liderDatasourceCfgFile = SetupUtils.writeToFile(liderDatasourceCfg, "tr.org.liderahenk.datasource.cfg");
				printMessage(Messages.getString("SUCCESSFULLY_CREATED_DATASOURCE_CFG_FILE"), display);

				printMessage(Messages.getString("SENDING_DATASOURCE_CFG_TO") + " " + nodeIp, display);
				manager.copyFileToRemote(liderDatasourceCfgFile,
						"/opt/" + PropertyReader.property("lider.package.name") + "/etc/", false);
				printMessage(Messages.getString("SUCCESSFULLY_SENT_DATASOURCE_CFG_TO") + " " + nodeIp, display);
				logger.log(Level.INFO, "Successfully sent tr.org.liderahenk.datasource.cfg to: {0}",
						new Object[] { nodeIp });
			} catch (CommandExecutionException e) {
				printMessage(Messages.getString("EXCEPTION_RAISED_WHILE_SENDING_DATASOURCE_CFG_TO") + " " + nodeIp,
						display);
				printMessage(Messages.getString("EXCEPTION_MESSAGE") + " " + e.getMessage() + " at " + nodeIp, display);
				logger.log(Level.SEVERE, e.getMessage());
				e.printStackTrace();
				throw new Exception();
			}

			printMessage(Messages.getString("INSTALLATION_COMPLETED_SUCCESSFULLY_AT") + " " + nodeIp, display);
			successfullSetup = true;

		} catch (Exception e) {
			printMessage(Messages.getString("INSTALLATION_FAILED_AT") + " " + nodeIp, display);
			e.printStackTrace();
		} finally {
			if (manager != null) {
				manager.disconnect();
			}
		}

		return successfullSetup;
	}

	/**
	 * Prints log message to the log console widget
	 * 
	 * @param message
	 */
	private void printMessage(final String message, Display display) {
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
				txtLogConsole.setSelection(txtLogConsole.getCharCount() - 1);
			}
		});
	}

	/**
	 * Reads file from classpath location of current project
	 * 
	 * @param fileName
	 */
	private String readFile(String fileName) {

		BufferedReader br = null;
		InputStream inputStream = null;

		String readingText = "";

		try {
			String currentLine;

			inputStream = this.getClass().getClassLoader().getResourceAsStream(fileName);

			br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

			while ((currentLine = br.readLine()) != null) {
				// Platform independent line separator.
				readingText += currentLine + System.getProperty("line.separator");
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				inputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return readingText;
	}

}
