package tr.org.liderahenk.installer.lider.wizard.pages;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tr.org.liderahenk.installer.lider.callables.LiderClusterInstallCallable;
import tr.org.liderahenk.installer.lider.config.LiderSetupConfig;
import tr.org.liderahenk.installer.lider.i18n.Messages;
import tr.org.liderahenk.installer.lider.utils.PageFlowHelper;
import tr.org.liderahenk.installer.lider.wizard.model.LiderNodeInfoModel;
import tr.org.pardus.mys.liderahenksetup.constants.NextPageEventType;
import tr.org.pardus.mys.liderahenksetup.exception.CommandExecutionException;
import tr.org.pardus.mys.liderahenksetup.exception.SSHConnectionException;
import tr.org.pardus.mys.liderahenksetup.utils.LiderAhenkUtils;
import tr.org.pardus.mys.liderahenksetup.utils.PropertyReader;
import tr.org.pardus.mys.liderahenksetup.utils.gui.GUIHelper;
import tr.org.pardus.mys.liderahenksetup.utils.setup.IOutputStreamProvider;
import tr.org.pardus.mys.liderahenksetup.utils.setup.SSHManager;

/**
 * @author <a href="mailto:caner.feyzullahoglu@agem.com.tr">Caner
 *         Feyzullahoglu</a>
 * 
 */
public class LiderClusterInstallationStatus extends WizardPage
		implements ILiderPage, ControlNextEvent, InstallationStatusPage {

	private LiderSetupConfig config;

	private ProgressBar progressBar;
	private Text txtLogConsole;

	private NextPageEventType nextPageEventType;

	boolean isInstallationFinished = false;
	boolean canGoBack = false;

	private final static String CLUSTER_CLIENTS = "server  server1 #NODE_IP:8181 check fall 3 id #CLIENT_ID inter 5000 rise 3 slowstart 120000 weight 50";
	private final static String CLUSTER_CLIENTS_SSL = "server  server1 #NODE_IP:8443 check fall 3 id #CLIENT_SSL_ID inter 5000 rise 3 slowstart 240000 weight 50";
	private final static String CLUSTER_SERVERS = "server  server1 #NODE_IP:5701 check fall 3 id #SERVER_ID inter 5000 rise 3 slowstart 60000 weight 50";

	private static final String INSTALL_WRAPPER = "/opt/{0}/bin/client -r 50 'wrapper:install'";

	private Integer clientId = 1005;
	private Integer clientSslId = 1008;
	private Integer serverId = 10011;

	private static final Logger logger = LoggerFactory.getLogger(LiderClusterInstallationStatus.class);

	public LiderClusterInstallationStatus(LiderSetupConfig config) {
		super(LiderClusterInstallationStatus.class.getName(), Messages.getString("LIDER_INSTALLATION"), null);
		setDescription(Messages.getString("LIDER_CLUSTER_INSTALLATION", "5.4"));
		this.config = config;
	}

	@Override
	public void createControl(Composite parent) {
		Composite container = GUIHelper.createComposite(parent, 1);
		setControl(container);

		txtLogConsole = GUIHelper.createText(container, new GridData(GridData.FILL_BOTH),
				SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
		txtLogConsole.setTopIndex(txtLogConsole.getLineCount() - 1);

		progressBar = new ProgressBar(container, SWT.SMOOTH | SWT.INDETERMINATE);
		progressBar.setSelection(0);
		progressBar.setMaximum(100);
		GridData progressGd = new GridData(GridData.FILL_HORIZONTAL);
		progressGd.heightHint = 40;
		// progressGd.widthHint = 780;
		progressBar.setLayoutData(progressGd);

	}

	@Override
	public IWizardPage getNextPage() {

		// Start Karaf installation here.
		// To prevent triggering installation again
		// (i.e. when clicked "next" after installation finished),
		// set isInstallationFinished to true when its done.
		if (super.isCurrentPage() && !isInstallationFinished
				&& nextPageEventType == NextPageEventType.CLICK_FROM_PREV_PAGE) {

			canGoBack = false;

			progressBar.setVisible(true);

			// Get display before new main runnable
			final Display display = Display.getCurrent();

			setPageCompleteAsync(isInstallationFinished, display);

			clearLogConsole(display);

			// Create a thread pool
			final ExecutorService executor = Executors.newFixedThreadPool(10);

			// Create future list that will keep the results of callables.
			final List<Future<Boolean>> resultList = new ArrayList<Future<Boolean>>();

			printMessage(Messages.getString("INITIALIZING_INSTALLATION"), display);

			// Create a main runnable and execute installations as new runnables
			// under this one. Because at the end of installation I have to wait
			// until all runnables completed and this situation locks GUI.
			Runnable mainRunnable = new Runnable() {
				@Override
				public void run() {

					// To identify first node
					int i = 1;
					for (Iterator<Entry<Integer, LiderNodeInfoModel>> iterator = config.getLiderNodeInfoMap().entrySet()
							.iterator(); iterator.hasNext(); i++) {

						Entry<Integer, LiderNodeInfoModel> entry = iterator.next();
						final LiderNodeInfoModel clusterNode = entry.getValue();

						Callable<Boolean> callable = new LiderClusterInstallCallable(clusterNode.getNodeIp(),
								clusterNode.getNodeRootPwd(), clusterNode.getNodeXmppResource(),
								clusterNode.getNodeXmppPresencePriority(), display, config, txtLogConsole, i == 1);

						Future<Boolean> result = executor.submit(callable);
						resultList.add(result);
					}

					try {
						executor.shutdown();
						executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

					boolean allNodesSuccess = false;

					if (resultList.size() > 0) {
						// Check if all nodes are properly installed
						for (Future<Boolean> future : resultList) {
							try {
								allNodesSuccess = future.get();
								if (!allNodesSuccess) {
									break;
								}
							} catch (Exception e) {
								e.printStackTrace();
								allNodesSuccess = false;
								break;
							}
						}
					} else {
						allNodesSuccess = true;
					}

					if (allNodesSuccess) {

						try {
							// And start other nodes.
							for (Iterator<Entry<Integer, LiderNodeInfoModel>> iterator = config.getLiderNodeInfoMap()
									.entrySet().iterator(); iterator.hasNext();) {

								Entry<Integer, LiderNodeInfoModel> entry = iterator.next();
								final LiderNodeInfoModel clusterNode = entry.getValue();

								installDependenciesToNode(clusterNode, display);

								sendCellarConfig(clusterNode, display);

								startNode(clusterNode, display);

							}

							// for (Iterator<Entry<Integer, LiderNodeInfoModel>>
							// iterator = config.getLiderNodeInfoMap()
							// .entrySet().iterator(); iterator.hasNext();) {
							//
							// Entry<Integer, LiderNodeInfoModel> entry =
							// iterator.next();
							// final LiderNodeInfoModel clusterNode =
							// entry.getValue();
							//
							// restartNode(clusterNode, display);
							// }

							for (Iterator<Entry<Integer, LiderNodeInfoModel>> iterator = config.getLiderNodeInfoMap()
									.entrySet().iterator(); iterator.hasNext();) {

								Entry<Integer, LiderNodeInfoModel> entry = iterator.next();
								final LiderNodeInfoModel clusterNode = entry.getValue();

								try {
									defineServiceForNode(clusterNode, display);
								} catch (Exception e) {
									if (e instanceof IOException) {
										Thread.sleep(5000);
										defineServiceForNode(clusterNode, display);
									} else {
										throw e;
									}
								}
							}

							// After Karaf nodes started, install HaProxy.
							installHaProxy(config.getLiderProxyAddress(), config.getLiderProxyPwd(),
									config.getLiderAccessKeyPath(), config.getLiderAccessPassphrase(),
									config.getLiderNodeInfoMap(), display);

							canGoBack = false;

							isInstallationFinished = true;

							display.asyncExec(new Runnable() {
								@Override
								public void run() {
									progressBar.setVisible(false);
								}
							});

							printMessage(Messages.getString("KARAF_CELLAR_INSTALLATION_FINISHED"), display);

							config.setInstallationFinished(isInstallationFinished);

							// To enable finish button
							setPageCompleteAsync(isInstallationFinished, display);

						} catch (Exception e) {
							e.printStackTrace();
							printMessage(Messages.getString("ERROR_OCCURED_WHILE_STARTING_OR_CONFIGURING_NODE"),
									display);
							printMessage(Messages.getString("ERROR_MESSAGE_", e.getMessage()), display);
							isInstallationFinished = false;
							// If any error occured user should be
							// able to go back and change selections
							// etc.
							canGoBack = true;
							display.asyncExec(new Runnable() {
								@Override
								public void run() {
									progressBar.setVisible(false);
								}
							});
						}

					} else {
						printMessage(Messages.getString("INSTALLER_WONT_CONTINUE_BECAUSE_ONE_OF_NODES_SETUP_FAILED"),
								display);
						isInstallationFinished = false;

						// If any error occured user should be
						// able to go back and change selections
						// etc.
						canGoBack = true;
						display.asyncExec(new Runnable() {
							@Override
							public void run() {
								progressBar.setVisible(false);
							}
						});

						// To enable finish button
						setPageCompleteAsync(isInstallationFinished, display);

						if (!isInstallationFinished) {
							try {
								openDownloadUrl();
							} catch (Exception e) {
								e.printStackTrace();
								txtLogConsole
										.setText((txtLogConsole.getText() != null && !txtLogConsole.getText().isEmpty()
												? txtLogConsole.getText() + "\n" : "")
												+ Messages.getString("CANNOT_OPEN_BROWSER_PLEASE_GO_TO") + "\n"
												+ PropertyReader.property("troubleshooting.url"));
							}
						}
					}

				}

			};
			Thread thread = new Thread(mainRunnable);
			thread.start();

		}
		// Select next page.
		return PageFlowHelper.selectNextPage(config, this);

	}

	private void openDownloadUrl() throws IOException {
		Runtime.getRuntime().exec("xdg-open " + PropertyReader.property("troubleshooting.url"));
	}

	private void sendCellarConfig(LiderNodeInfoModel clusterNode, Display display) throws Exception {

		SSHManager manager = null;
		File cellarCfg;
		InputStream inputStream;

		// Send org.apache.karaf.cellar.groups.cfg
		try {
			manager = new SSHManager(clusterNode.getNodeIp(), "root", clusterNode.getNodeRootPwd(),
					config.getLiderPort(), config.getLiderAccessKeyPath(), config.getLiderAccessPassphrase());
			manager.connect();

			printMessage(Messages.getString("CREATING_CELLAR_CFG_FILE"), display);
			inputStream = this.getClass().getClassLoader().getResourceAsStream("org.apache.karaf.cellar.groups.cfg");
			cellarCfg = LiderAhenkUtils.streamToFile(inputStream, "org.apache.karaf.cellar.groups.cfg");
			printMessage(Messages.getString("SUCCESSFULLY_CREATED_CELLAR_CFG_FILE"), display);

			String copyPath = "/opt/" + PropertyReader.property("lider.package.name") + "/etc/";

			printMessage(Messages.getString("SENDING_CELLAR_CFG_FILE_TO_", clusterNode.getNodeIp()), display);
			manager.copyFileToRemote(cellarCfg, copyPath, false);
			printMessage(Messages.getString("SUCCESSFULLY_SENT_CELLAR_CFG_FILE_TO_", clusterNode.getNodeIp()), display);

			logger.info("Successfully sent org.apache.karaf.cellar.groups.cfg to: {}",
					new Object[] { clusterNode.getNodeIp() });
		} catch (CommandExecutionException e) {
			printMessage(Messages.getString("EXCEPTION_RAISED_WHILE_SENDING_CELLAR_CFG_AT_", clusterNode.getNodeIp()),
					display);
			printMessage(Messages.getString("EXCEPTION_MESSAG_AT", e.getMessage(), clusterNode.getNodeIp()), display);
			logger.error(e.getMessage(), e);
			throw new Exception();
		} finally {
			if (manager != null) {
				manager.disconnect();
			}
		}

	}

	private void restartNode(LiderNodeInfoModel clusterNode, Display display) throws Exception {

		SSHManager manager = null;

		try {
			manager = new SSHManager(clusterNode.getNodeIp(), "root", clusterNode.getNodeRootPwd(),
					config.getLiderPort(), config.getLiderAccessKeyPath(), config.getLiderAccessPassphrase());
			manager.connect();

			printMessage(Messages.getString("RESTARTING_CELLAR_NODE_AT", clusterNode.getNodeIp()), display);
			try {
				manager.execCommand("pgrep -f karaf | xargs kill", new Object[] {});
			} catch (CommandExecutionException e) {
			}

			manager.execCommand("/opt/" + PropertyReader.property("lider.package.name") + "/bin/start",
					new Object[] {});

			Thread.sleep(30000);

			printMessage(Messages.getString("SUCCESSFULLY_RESTARTED_CELLAR_NODE_AT", clusterNode.getNodeIp()), display);
			logger.info("Successfully restarted Cellar node at {}", new Object[] { clusterNode.getNodeIp() });
		} catch (SSHConnectionException e) {
			printMessage(Messages.getString("COULD_NOT_CONNECT_TO_", clusterNode.getNodeIp()), display);
			printMessage(Messages.getString("ERROR_MESSAGE_", e.getMessage()), display);
			logger.error(e.getMessage(), e);
			throw new Exception();

		} catch (CommandExecutionException e) {
			printMessage(
					Messages.getString("EXCEPTION_RAISED_WHILE_RESTARTING_CELLAR_NODE_AT", clusterNode.getNodeIp()),
					display);
			printMessage(Messages.getString("EXCEPTION_MESSAGE_AT", e.getMessage(), clusterNode.getNodeIp()), display);
			logger.error(e.getMessage(), e);
			throw new Exception();
		} finally {
			if (manager != null) {
				manager.disconnect();
			}
		}

	}

	private void installDependenciesToNode(LiderNodeInfoModel clusterNode, Display display) throws Exception {

		SSHManager manager = null;

		try {
			manager = new SSHManager(clusterNode.getNodeIp(), "root", clusterNode.getNodeRootPwd(),
					config.getLiderPort(), config.getLiderAccessKeyPath(), config.getLiderAccessPassphrase());
			manager.connect();

			printMessage(Messages.getString("UPDATING_PACKAGE_LIST_OF_", clusterNode.getNodeIp()), display);
			manager.execCommand("apt-get -y --force-yes update", new Object[] {});
			printMessage(Messages.getString("SUCCESSFULLY_UPDATED_PACKAGE_LIST_OF_", clusterNode.getNodeIp()), display);

			printMessage(Messages.getString("INSTALLING_DEPENDENCIES_AT_", clusterNode.getNodeIp()), display);
			manager.execCommand("apt-get install -y --force-yes openjdk-8-jdk sshpass rsync nmap", new Object[] {});
			printMessage(Messages.getString("SUCCESSFULLY_INSTALLED_DEPENDENCIES_AT_", clusterNode.getNodeIp()),
					display);
			logger.info("Successfully installed dependencies at {}", new Object[] { clusterNode.getNodeIp() });

		} catch (SSHConnectionException e) {
			printMessage(Messages.getString("COULD_NOT_CONNECT_TO_", clusterNode.getNodeIp()), display);
			printMessage(Messages.getString("ERROR_MESSAGE_", e.getMessage()), display);
			logger.error(e.getMessage(), e);
			throw new Exception();

		} catch (CommandExecutionException e) {
			printMessage(
					Messages.getString("EXCEPTION_RAISED_WHILE_INSTALLING_DEPENDENCIES_AT_", clusterNode.getNodeIp()),
					display);
			printMessage(Messages.getString("EXCEPTION_MESSAGE_AT", e.getMessage(), clusterNode.getNodeIp()), display);
			logger.error(e.getMessage(), e);
			throw new Exception();
		} finally {
			if (manager != null) {
				manager.disconnect();
			}
		}

	}

	private void startNode(LiderNodeInfoModel clusterNode, Display display) throws Exception {

		SSHManager manager = null;

		try {

			manager = new SSHManager(clusterNode.getNodeIp(), "root", clusterNode.getNodeRootPwd(),
					config.getLiderPort(), config.getLiderAccessKeyPath(), config.getLiderAccessPassphrase());
			manager.connect();

			printMessage(Messages.getString("STARTING_CELLAR_NODE_AT", clusterNode.getNodeIp()), display);
			manager.execCommand("/opt/" + PropertyReader.property("lider.package.name") + "/bin/start", new Object[] {},
					new IOutputStreamProvider() {
						@Override
						public byte[] getStreamAsByteArray() {
							return ("\n").getBytes(StandardCharsets.UTF_8);
						}
					}, false);

			Thread.sleep(20000);

			printMessage(Messages.getString("SUCCESSFULLY_STARTED_CELLAR_NODE_AT", clusterNode.getNodeIp()), display);
			logger.info("Successfully started Cellar node at {}", new Object[] { clusterNode.getNodeIp() });

		} catch (SSHConnectionException e) {
			printMessage(Messages.getString("COULD_NOT_CONNECT_TO_", clusterNode.getNodeIp()), display);
			printMessage(Messages.getString("ERROR_MESSAGE_", e.getMessage()), display);
			logger.error(e.getMessage(), e);
			throw new Exception();

		} catch (CommandExecutionException e) {
			printMessage(Messages.getString("EXCEPTION_RAISED_WHILE_STARTING_CELLAR_NODE_AT", clusterNode.getNodeIp()),
					display);
			printMessage(Messages.getString("EXCEPTION_MESSAGE_AT", e.getMessage(), clusterNode.getNodeIp()), display);
			logger.error(e.getMessage(), e);
			throw new Exception();
		} finally {
			if (manager != null) {
				manager.disconnect();
			}
		}

	}

	private void defineServiceForNode(LiderNodeInfoModel clusterNode, Display display) throws Exception {

		SSHManager manager = null;

		// Install service wrapper
		try {

			manager = new SSHManager(clusterNode.getNodeIp(), "root", clusterNode.getNodeRootPwd(),
					config.getLiderPort(), config.getLiderAccessKeyPath(), config.getLiderAccessPassphrase());
			manager.connect();

			printMessage(Messages.getString("INSTALLING_WRAPPER_AT_NODE_", clusterNode.getNodeIp()), display);
			manager.execCommand(INSTALL_WRAPPER, new Object[] { PropertyReader.property("lider.package.name") },
					new IOutputStreamProvider() {
						@Override
						public byte[] getStreamAsByteArray() {
							return "\n".getBytes(StandardCharsets.UTF_8);
						}
					}, true);
			Thread.sleep(3000);
			printMessage(Messages.getString("SUCCESSFULLY_INSTALLED_WRAPPER_AT_NODE_", clusterNode.getNodeIp()),
					display);

			logger.info("Successfully installed service wrapper at node {}", new Object[] { clusterNode.getNodeIp() });

		} catch (SSHConnectionException e) {
			printMessage(Messages.getString("COULD_NOT_CONNECT_TO_", clusterNode.getNodeIp()), display);
			printMessage(Messages.getString("ERROR_MESSAGE_", e.getMessage()), display);
			logger.error(e.getMessage(), e);
			throw new Exception();
		} catch (CommandExecutionException e) {
			printMessage(Messages.getString("EXCEPTION_RAISED_WHILE_INSTALLING_SERVICE_WRAPPER_AT_NODE",
					clusterNode.getNodeIp()), display);
			printMessage(Messages.getString("EXCEPTION_MESSAGE_AT", e.getMessage(), clusterNode.getNodeIp()), display);
			logger.error(e.getMessage(), e);
			throw e;
		} finally {
			if (manager != null) {
				manager.disconnect();
			}
		}

		try {
			manager = new SSHManager(clusterNode.getNodeIp(), "root", clusterNode.getNodeRootPwd(),
					config.getLiderPort(), config.getLiderAccessKeyPath(), config.getLiderAccessPassphrase());
			manager.connect();

			printMessage(Messages.getString("MODIFYING_KARAF_WRAPPER_CONF_AT_", clusterNode.getNodeIp()), display);
			manager.execCommand(
					"sed -i '/set.default.JAVA_HOME/c\\set.default.JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64/jre' /opt/{0}/etc/karaf-wrapper.conf",
					//"sed -i '/set.default.JAVA_HOME/c\\set.default.JAVA_HOME=/usr/lib/jvm/java-7-openjdk-amd64/jre' /opt/{0}/etc/karaf-wrapper.conf",
					
					new Object[] { PropertyReader.property("lider.package.name") });
			printMessage(Messages.getString("SUCCESSFULLY_MODIFIED_KARAF_WRAPPER_CONF_AT_", clusterNode.getNodeIp()),
					display);
			logger.info("Successfully modified Karaf wrapper at {}", new Object[] { clusterNode.getNodeIp() });

		} catch (SSHConnectionException e) {
			printMessage(Messages.getString("COULD_NOT_CONNECT_TO_", clusterNode.getNodeIp()), display);
			printMessage(Messages.getString("ERROR_MESSAGE_", e.getMessage()), display);
			logger.error(e.getMessage(), e);
			throw new Exception();

		} catch (CommandExecutionException e) {
			printMessage(Messages.getString("EXCEPTION_RAISED_WHILE_MODIFYING_KARAF_WRAPPER_CONF_AT_",
					clusterNode.getNodeIp()), display);
			printMessage(Messages.getString("EXCEPTION_MESSAGE_AT", e.getMessage(), clusterNode.getNodeIp()), display);
			logger.error(e.getMessage(), e);
			throw new Exception();
		} finally {
			if (manager != null) {
				manager.disconnect();
			}
		}

		try {
			manager = new SSHManager(clusterNode.getNodeIp(), "root", clusterNode.getNodeRootPwd(),
					config.getLiderPort(), config.getLiderAccessKeyPath(), config.getLiderAccessPassphrase());
			manager.connect();

			printMessage(Messages.getString("DEFINING_KARAF_SERVICE_AT_", clusterNode.getNodeIp()), display);

			manager.execCommand(
					"ln -fs /opt/" + PropertyReader.property("lider.package.name")
							+ "/bin/karaf-service /etc/init.d/ && update-rc.d karaf-service defaults",
					new IOutputStreamProvider() {
						@Override
						public byte[] getStreamAsByteArray() {
							return "\n".getBytes(StandardCharsets.UTF_8);
						}
					}, true);

			manager.execCommand("update-rc.d karaf-service defaults", new IOutputStreamProvider() {
				@Override
				public byte[] getStreamAsByteArray() {
					return "\n".getBytes(StandardCharsets.UTF_8);
				}
			}, true);

			printMessage(Messages.getString("SUCCESSFULLY_DEFINED_SERVICE_AT_", clusterNode.getNodeIp()), display);
			logger.info("Successfully defined service at {}", new Object[] { clusterNode.getNodeIp() });

		} catch (SSHConnectionException e) {
			printMessage(Messages.getString("COULD_NOT_CONNECT_TO_", clusterNode.getNodeIp()), display);
			printMessage(Messages.getString("ERROR_MESSAGE_", e.getMessage()), display);
			logger.error(e.getMessage(), e);
			throw new Exception();

		} catch (CommandExecutionException e) {
			printMessage(Messages.getString("EXCEPTION_RAISED_WHILE_KARAF_AS_SERVICE_AT_", clusterNode.getNodeIp()),
					display);
			printMessage(Messages.getString("EXCEPTION_MESSAGE_AT", e.getMessage(), clusterNode.getNodeIp()), display);
			logger.error(e.getMessage(), e);
			throw new Exception();
		} finally {
			if (manager != null) {
				manager.disconnect();
			}
		}
	}

	private void installHaProxy(String liderProxyAddress, String liderProxyPwd, String liderAccessKeyPath,
			String liderAccessPassphrase, Map<Integer, LiderNodeInfoModel> liderNodeInfoMap, Display display)
			throws Exception {

		SSHManager manager = null;
		try {
			manager = new SSHManager(liderProxyAddress, "root", liderProxyPwd, config.getLiderPort(),
					config.getLiderAccessKeyPath(), config.getLiderAccessPassphrase());
			manager.connect();

			printMessage(Messages.getString("INSTALLING_HAPROXY_PACKAGE_TO_", liderProxyAddress), display);
			manager.execCommand("apt-get -y --force-yes install haproxy", new Object[] {});
			printMessage(Messages.getString("SUCCESSFULLY_INSTALLED_HAPROXY_PACKAGE_TO_", liderProxyAddress), display);
			logger.info("Successfully installed HaProxy to {}", new Object[] { liderProxyAddress });

		} catch (SSHConnectionException e) {
			printMessage(Messages.getString("COULD_NOT_CONNECT_TO_", liderProxyAddress), display);
			printMessage(Messages.getString("ERROR_MESSAGE_", e.getMessage()), display);
			logger.error(e.getMessage(), e);
			throw new Exception();

		} catch (CommandExecutionException e) {
			printMessage(Messages.getString("EXCEPTION_RAISED_WHILE_INSTALLING_HAPROXY_PACKAGE_AT_", liderProxyAddress),
					display);
			printMessage(Messages.getString("EXCEPTION_MESSAGE_AT", e.getMessage(), liderProxyAddress), display);
			logger.error(e.getMessage(), e);
			throw new Exception();
		}

		printMessage(Messages.getString("PREPARING_BACKEND_PROPERTIES"), display);
		Map<String, String> propertyMap = prepareBackendProperties();
		printMessage(Messages.getString("SUCCESSFULLY_PREPARED_BACKEND_PROPERTIES"), display);

		printMessage(Messages.getString("CREATING_HAPROXY_CONFIG_FILE"), display);
		String haproxyCfg = readFile("haproxy_karaf.cfg");

		Map<String, String> map = new HashMap<>();
		map.put("#HAPROXY_ADDRESS", config.getLiderProxyAddress());
		map.put("#CLUSTER_CLIENTS", propertyMap.get("CLUSTER_CLIENTS"));
		map.put("#CLUSTER_CLIENTS_SSL", propertyMap.get("CLUSTER_CLIENTS_SSL"));
		map.put("#CLUSTER_SERVERS", propertyMap.get("CLUSTER_SERVERS"));

		haproxyCfg = LiderAhenkUtils.replace(map, haproxyCfg);
		File haproxyCfgFile = LiderAhenkUtils.writeToFile(haproxyCfg, "haproxy.cfg");
		printMessage(Messages.getString("SUCCESSFULLY_CREATED_HAPROXY_CONFIG_FILE"), display);
		logger.info("Successfully created haproxy.cfg");

		try {
			printMessage(Messages.getString("SENDING_HAPROXY_CONFIG_FILE_TO_", liderProxyAddress), display);
			manager.copyFileToRemote(haproxyCfgFile, "/etc/haproxy/", false);
			printMessage(Messages.getString("SUCCESSFULLY_SENT_HAPROXY_CONFIG_FILE_TO_", liderProxyAddress), display);
			logger.info("Successfully sent haproxy.cfg to {}", new Object[] { liderProxyAddress });

		} catch (CommandExecutionException e) {
			printMessage(Messages.getString("EXCEPTION_RAISED_WHILE_SENDING_HAPROXY_CFG_TO_", liderProxyAddress),
					display);
			printMessage(Messages.getString("EXCEPTION_MESSAGE_AT", e.getMessage(), liderProxyAddress), display);
			logger.error(e.getMessage(), e);
			throw new Exception();
		}

		try {
			printMessage(Messages.getString("RESTARTING_HAPROXY_SERVICE_AT_", liderProxyAddress), display);
			manager.execCommand("service haproxy restart", new Object[] {});
			printMessage(Messages.getString("SUCCESSFULLY_RESTARTED_HAPROXY_SERVICE_AT_", liderProxyAddress), display);
			logger.info("Successfully restarted haproxy service at {}", new Object[] { liderProxyAddress });

		} catch (CommandExecutionException e) {
			printMessage(Messages.getString("EXCEPTION_RAISED_WHILE_RESTARTING_HAPROXY_SERVICE_AT_", liderProxyAddress),
					display);
			printMessage(Messages.getString("EXCEPTION_MESSAGE_AT", e.getMessage(), liderProxyAddress), display);
			logger.error(e.getMessage(), e);
			throw new Exception();
		} finally {
			if (manager != null) {
				manager.disconnect();
			}
		}

		printMessage(Messages.getString("SUCCESSFULLY_COMPLETED_INSTALLATION_OF_HAPROXY_AT_", liderProxyAddress),
				display);
		logger.info("Successfully completed installation of HAProxy at: {0}", new Object[] { liderProxyAddress });
	}

	private Map<String, String> prepareBackendProperties() {

		Map<String, String> propertyMap = new HashMap<String, String>();

		String clusterClients = "";
		String clusterClientsSsl = "";
		String clusterServers = "";

		for (Iterator<Entry<Integer, LiderNodeInfoModel>> iterator = config.getLiderNodeInfoMap().entrySet()
				.iterator(); iterator.hasNext();) {

			Entry<Integer, LiderNodeInfoModel> entry = iterator.next();
			final LiderNodeInfoModel clusterNode = entry.getValue();

			clusterClients += CLUSTER_CLIENTS.replace("#NODE_IP", clusterNode.getNodeIp()).replace("#CLIENT_ID",
					clientId.toString());
			clusterClients += "\n\t";
			++clientId;
			clusterClientsSsl += CLUSTER_CLIENTS_SSL.replace("#NODE_IP", clusterNode.getNodeIp())
					.replace("#CLIENT_SSL_ID", clientSslId.toString());
			clusterClientsSsl += "\n\t";
			++clientSslId;
			clusterServers += CLUSTER_SERVERS.replace("#NODE_IP", clusterNode.getNodeIp()).replace("#SERVER_ID",
					serverId.toString());
			clusterServers += "\n\t";
			++serverId;
		}

		propertyMap.put("CLUSTER_CLIENTS", clusterClients);
		propertyMap.put("CLUSTER_CLIENTS_SSL", clusterClientsSsl);
		propertyMap.put("CLUSTER_SERVERS", clusterServers);

		return propertyMap;
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
	 * Sets page complete status asynchronously.
	 * 
	 * @param isComplete
	 */
	private void setPageCompleteAsync(final boolean isComplete, Display display) {
		display.asyncExec(new Runnable() {
			@Override
			public void run() {
				setPageComplete(isComplete);
			}
		});
	}

	/**
	 * Clears log console by set its content to empty string.
	 */
	private void clearLogConsole(Display display) {
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