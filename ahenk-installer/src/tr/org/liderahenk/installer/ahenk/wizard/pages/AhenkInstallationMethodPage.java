package tr.org.liderahenk.installer.ahenk.wizard.pages;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import tr.org.liderahenk.installer.ahenk.config.AhenkSetupConfig;
import tr.org.liderahenk.installer.ahenk.i18n.Messages;
import tr.org.pardus.mys.liderahenksetup.constants.InstallMethod;
import tr.org.pardus.mys.liderahenksetup.constants.NextPageEventType;
import tr.org.pardus.mys.liderahenksetup.utils.LiderAhenkUtils;

/**
 * @author caner Caner Feyzullahoğlu caner.feyzullahoglu@agem.com.tr
 */
public class AhenkInstallationMethodPage extends WizardPage {

	private AhenkSetupConfig config = null;

	// Widgets
	private Composite mainContainer = null;
	private Composite fileDialogContainer = null;
	private Button useDebBtn = null;
	private Button useWgetBtn = null;
	private FileDialog fileDialog = null;
	private Text fileDialogText = null;
	private Button fileDialogBtn = null;
	private String fileDialogResult = null;
	private Text downloadUrlTxt = null;
	private byte[] debContent;

	public AhenkInstallationMethodPage(AhenkSetupConfig config) {
		super(AhenkInstallationMethodPage.class.getName(), Messages.getString("INSTALLATION_OF_AHENK"), null);
		setDescription(Messages.getString("BY_WHICH_WAY_WOULD_YOU_LIKE_TO_INSTALL_AHENK"));
		this.config = config;
	}

	@Override
	public void createControl(Composite parent) {

		// create main container
		mainContainer = new Composite(parent, SWT.NONE);
		mainContainer.setLayout(new GridLayout(1, false));
		setControl(mainContainer);

		// Install by given .deb package
		useDebBtn = new Button(mainContainer, SWT.RADIO);
		useDebBtn.setText(Messages.getString("INSTALL_FROM_GIVEN_DEB_PACKAGE"));
		useDebBtn.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (useDebBtn.getSelection()) {
					fileDialogText.setEnabled(true);
					fileDialogBtn.setEnabled(true);
					downloadUrlTxt.setEnabled(false);
					updatePageCompleteStatus();
				}
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});
		useDebBtn.setSelection(true);

		fileDialogContainer = new Composite(mainContainer, SWT.NONE);
		GridLayout glFileDialog = new GridLayout(2, false);
		glFileDialog.marginLeft = 15;
		// Adjust button near to text field
		glFileDialog.horizontalSpacing = -3;
		fileDialogContainer.setLayout(glFileDialog);

		// File dialog window
		fileDialog = new FileDialog(mainContainer.getShell(), SWT.SAVE);
		fileDialog.setText(Messages.getString("UPLOAD_AHENK"));
		fileDialog.setFilterExtensions(new String[] { "*.deb" });

		// Upload key text field
		fileDialogText = new Text(fileDialogContainer, SWT.BORDER);
		fileDialogText.setEnabled(false);
		fileDialogText.setEditable(false);
		GridData gdFileDialogTxt = new GridData();
		gdFileDialogTxt.widthHint = 247;
		fileDialogText.setLayoutData(gdFileDialogTxt);
		fileDialogText.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				updatePageCompleteStatus();
			}
		});

		// Copy ahenk.deb to /tmp and bring it as default deb in page
		InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("ahenk_1.0.0-6_all.deb");
		File ahenkDeb = LiderAhenkUtils.streamToFile(inputStream, "ahenk_1.0.0-6_all.deb");
		fileDialogText.setText(ahenkDeb.getAbsolutePath());

		// Set file to config as array of bytes
		debContent = new byte[(int) ahenkDeb.length()];

		FileInputStream stream = null;
		try {
			stream = new FileInputStream(ahenkDeb);
			stream.read(debContent);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		} finally {
			try {
				stream.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}

		config.setDebFileAbsPath(fileDialogText.getText());

		InputStream input = this.getClass().getClassLoader().getResourceAsStream("log.conf");
		File logConf = LiderAhenkUtils.streamToFile(input, "log.conf");

		// Set file to config as array of bytes
		debContent = new byte[(int) logConf.length()];

		FileInputStream inStream = null;
		try {
			inStream = new FileInputStream(logConf);
			inStream.read(debContent);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		} finally {
			try {
				inStream.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}

		config.setAhenkLogConfAbsPath(logConf.getAbsolutePath());

		// Upload Ahenk .deb push button
		fileDialogBtn = new Button(fileDialogContainer, SWT.PUSH);
		fileDialogBtn.setText(Messages.getString("UPLOAD_AHENK"));

		GridData gdFileDialogBtn = new GridData();
		gdFileDialogBtn.heightHint = 25;
		gdFileDialogBtn.widthHint = 125;
		fileDialogBtn.setLayoutData(gdFileDialogBtn);
		fileDialogBtn.setEnabled(true);

		fileDialogBtn.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				fileDialogResult = fileDialog.open();
				if (fileDialogResult != null && !"".equals(fileDialogResult)) {
					fileDialogText.setText(fileDialogResult);
				}
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		// Install by given URL
		useWgetBtn = new Button(mainContainer, SWT.RADIO);
		useWgetBtn.setText(Messages.getString("INSTALL_FROM_GIVEN_URL"));

		useWgetBtn.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (useWgetBtn.getSelection()) {
					downloadUrlTxt.setEnabled(true);
					fileDialogText.setEnabled(false);
					fileDialogBtn.setEnabled(false);
					updatePageCompleteStatus();
				}
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		Composite downloadUrlContainer = new Composite(mainContainer, SWT.NONE);
		GridLayout glDownloadUrl = new GridLayout(1, false);
		downloadUrlContainer.setLayout(glDownloadUrl);

		downloadUrlTxt = new Text(downloadUrlContainer, SWT.BORDER);
		GridData gdDownloadUrlTxt = new GridData();
		gdDownloadUrlTxt.widthHint = 350;
		downloadUrlTxt.setLayoutData(gdDownloadUrlTxt);
		downloadUrlTxt.setEnabled(false);

		downloadUrlTxt.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				updatePageCompleteStatus();
			}
		});

		Composite warningComp = new Composite(downloadUrlContainer, SWT.NONE);
		warningComp.setLayout(new GridLayout(1, false));
		new Label(warningComp, SWT.NONE);

		Label label1 = new Label(warningComp, SWT.NONE);
		label1.setText(
				"Kuruluma uygun deb dosyası varsayılan olarak getirilmiştir.\nHazır getirilen deb dosyasıyla kuruluma devam edebilirsiniz.");
		label1.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_DARK_RED));
	}

	private void updatePageCompleteStatus() {
		// If install from deb is selected path of .deb file must be given
		if (useDebBtn.getSelection()) {
			if (fileDialogText.getText() != null && !"".equals(fileDialogText.getText())) {
				setPageComplete(true);
			} else {
				setPageComplete(false);
			}
		} else {
			// If install from URL is selected URL must be given
			if (downloadUrlTxt.getText() != null && !"".equals(downloadUrlTxt.getText())) {
				setPageComplete(true);
			} else {
				setPageComplete(false);
			}
		}
	}

	@Override
	public IWizardPage getNextPage() {

		if (useDebBtn.getSelection()) {
			config.setAhenkInstallMethod(InstallMethod.PROVIDED_DEB);
			config.setDebFileAbsPath(fileDialogText.getText());
		} else {
			config.setAhenkInstallMethod(InstallMethod.WGET);
			config.setAhenkDownloadUrl(downloadUrlTxt.getText());
		}

		((ControlNextEvent) super.getNextPage()).setNextPageEventType(NextPageEventType.CLICK_FROM_PREV_PAGE);

		AhenkConfPage confPage = ((AhenkConfPage) super.getNextPage());
		confPage.getNextPage();
		confPage.setNextPageEventType(NextPageEventType.CLICK_FROM_PREV_PAGE);

		return confPage;
	}

}
