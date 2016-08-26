package tr.org.liderahenk.installer.lider.wizard.pages;

import java.util.Iterator;
import java.util.Map.Entry;

import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import tr.org.liderahenk.installer.lider.config.LiderSetupConfig;
import tr.org.liderahenk.installer.lider.i18n.Messages;
import tr.org.liderahenk.installer.lider.wizard.model.XmppNodeInfoModel;
import tr.org.pardus.mys.liderahenksetup.constants.NextPageEventType;
import tr.org.pardus.mys.liderahenksetup.utils.gui.GUIHelper;

/**
 * @author <a href="mailto:caner.feyzullahoglu@agem.com.tr">Caner Feyzullahoglu</a>
 * 
 */
public class XmppClusterConfirmPage extends WizardPage implements IXmppPage {

	private LiderSetupConfig config;

	private Label lblIp;

	public XmppClusterConfirmPage(LiderSetupConfig config) {
		super(XmppClusterConfirmPage.class.getName(), Messages.getString("LIDER_INSTALLATION"), null);
		setDescription("4.3 " + Messages.getString("EJABBERD_CLUSTER_CONFIRM"));
		this.config = config;
	}

	@Override
	public void createControl(Composite parent) {

		Composite container = GUIHelper.createComposite(parent, 1);
		setControl(container);

		GridData gd = new GridData();
		gd.widthHint = 700;
		gd.minimumWidth = 200;
		lblIp = GUIHelper.createLabel(container, "localhost");
		lblIp.setLayoutData(gd);

		GUIHelper.createLabel(container, "- " + Messages.getString("USE_DEFAULT_REPOSITORY"));

		GUIHelper.createLabel(container, Messages.getString("EJABBERD_CLUSTER_WILL_BE_INSTALLED") + " "
				+ Messages.getString("WANT_TO_CONTINUE_PRESS_NEXT"));
	}

	@Override
	public IWizardPage getNextPage() {
		
		String ipList = "";
		for (Iterator<Entry<Integer, XmppNodeInfoModel>> iterator = config.getXmppNodeInfoMap().entrySet()
				.iterator(); iterator.hasNext();) {

			Entry<Integer, XmppNodeInfoModel> entry = iterator.next();
			final XmppNodeInfoModel clusterNode = entry.getValue();
			ipList += clusterNode.getNodeIp() + ", ";
		}
		
		
		// Set the IP info in the opening of page
		lblIp.setText("- IP: " + ipList);

		((ControlNextEvent) super.getNextPage()).setNextPageEventType(NextPageEventType.CLICK_FROM_PREV_PAGE);

		// Set page complete to true, otherwise it does not go into getNextPage
		// method of DatabaseInstallationStatus page.
		((WizardPage) super.getNextPage()).setPageComplete(true);

		// Set global variable to false before every installation status page,
		// if it is not set and there are more than one component to be
		// installed, finish button will be enabled directly in the last
		// installation page.
		config.setInstallationFinished(false);

		return super.getNextPage();
	}
}
