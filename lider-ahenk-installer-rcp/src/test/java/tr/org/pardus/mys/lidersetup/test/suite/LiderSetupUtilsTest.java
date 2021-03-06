package tr.org.pardus.mys.lidersetup.test.suite;

import java.io.File;

import junit.framework.TestCase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import tr.org.pardus.mys.liderahenksetup.exception.CommandExecutionException;
import tr.org.pardus.mys.liderahenksetup.exception.SSHConnectionException;
import tr.org.pardus.mys.liderahenksetup.utils.setup.SetupUtils;

@RunWith(JUnit4.class)
public class LiderSetupUtilsTest extends TestCase {

	@Test
	public void sshConnectionShouldSucceed() {
		assertEquals(true, SetupUtils.canConnectViaSsh("192.168.1.81", "root", "agem5644"));
		assertEquals(true, SetupUtils.canConnectViaSsh("192.168.1.40", "pardus", "agem5644"));
	}
	
	@Test
	public void sshConnectionShouldFail() {
		assertEquals(false, SetupUtils.canConnectViaSsh("192.168.1.81", "root", "wrongpwd"));
		assertEquals(false, SetupUtils.canConnectViaSsh("192.168.1.40", "pardus", "wrongpwd"));
	}
	
	@Test
	public void packageShouldExistOnRemote() {
		boolean result = false;
		try {
			result = SetupUtils.packageExists("192.168.1.40", "pardus", "agem5644", 22, null, "gedit", "3.4.2-1");
		} catch(Exception e) {
		}
		assertEquals(true, result);
		result = false;
		try {
			result = SetupUtils.packageExists("192.168.1.40", "pardus", "agem5644", 22, null, "gedit", "3.4.2-1");
		} catch(Exception e) {
		}
		assertEquals(true, result);
	}
	
	@Test
	public void packageShouldExistOnLocal() {
		boolean result = false;
		try {
			result = SetupUtils.packageExists("localhost", null, "agem5644", null, null, "gedit", "2.30.5+qiana");
		} catch (Exception e) {
		}
		assertEquals(true, result);
	}
	
	@Test
	public void packageShouldNotExistOnRemote() {
		boolean result = true;
		try {
			result = SetupUtils.packageExists("192.168.1.40", "pardus", "agem5644", 22, null, "virtualbox", "5.0.0");
		} catch (Exception e) {
		}
		assertEquals(false, result);
		result = true;
		try {
			result = SetupUtils.packageExists("192.168.1.40", "pardus", "agem5644", 22, null, "virtualbox", "5.0.0");
		} catch (Exception e) {
		}
		assertEquals(false, result);
	}
	
	@Test
	public void packageShouldNotExistOnLocal() {
		boolean result = true;
		try {
			result = SetupUtils.packageExists("localhost", null, "agem5644", null, null, "virtualbox", "5.0.0");
		} catch (Exception e) {
		}
		assertEquals(false, result);
	}
	
	@Test
	public void packageShouldBeInstalledOnRemote() throws SSHConnectionException, CommandExecutionException {
		SetupUtils.installPackage("192.168.1.40", "root", "agem5644", 22, null, "vim", "2:7.3.547-7");
	}
	
	@Test
	public void packageShouldBeInstalledOnLocal() throws SSHConnectionException, CommandExecutionException {
		SetupUtils.installPackage("localhost", null, "agem5644", null, null, "vim", "2:7.4.052-1ubuntu3");
	}
	
	@Test
	public void packageShouldBeUninstalledOnRemote() throws CommandExecutionException, SSHConnectionException {
		SetupUtils.uninstallPackage("192.168.1.40", "root", "agem5644", 22, null, "gedit");
	}
	
	@Test
	public void packageShouldBeUninstalledOnLocal() throws CommandExecutionException, SSHConnectionException {
		SetupUtils.uninstallPackage("localhost", null, "agem5644", null, null, "vim");
	}
	
	@Test
	public void repoShouldBeAddedOnRemote() throws CommandExecutionException, SSHConnectionException {
		SetupUtils.addRepository("192.168.1.77", "root", "agem5644", 22, null, "ppa:andrei-pozolotin/maven3");
	}
	
	@Test
	public void repoShouldBeAddedOnLocal() throws CommandExecutionException, SSHConnectionException {
		SetupUtils.addRepository("localhost", null, "agem5644", null, null, "ppa:andrei-pozolotin/maven3");
	}
	
	@Test
	public void fileShouldBeCopiedToRemote() throws SSHConnectionException, CommandExecutionException {
		File localFile = new File(this.getClass().getClassLoader().getResource("dummy-file").getFile());
		assertNotNull(localFile);
		SetupUtils.copyFile("192.168.1.40", "root", "agem5644", 22, null, localFile, "/home/pardus/");
	}
	
	@Test
	public void fileShouldBeCopiedOnLocal() throws SSHConnectionException, CommandExecutionException {
		File localFile = new File(this.getClass().getClassLoader().getResource("dummy-file").getFile());
		assertNotNull(localFile);
		SetupUtils.copyFile("localhost", null, "agem5644", null, null, localFile, "/home/emre/");
	}
	
	@Test
	public void packageShouldAlreadyBeInstalledOnLocal() {
		boolean result = false;
		try {
			result = SetupUtils.packageInstalled("localhost", null, "agem5644", null, null, "gedit", "2.30.5+qiana");
		} catch (Exception e) {
		}
		assertEquals(true, result);
	}
	
	@Test
	public void packageShouldAlreadyBeInstalledOnRemote() {
		boolean result = false;
		try {
			result = SetupUtils.packageInstalled("192.168.1.40", "pardus", "agem5644", 22, null, "vim", "2:7.3.547-7");
		} catch (Exception e) {
		}
		assertEquals(true, result);
	}
	
	@Test
	public void packageShouldNotInstalledOnLocal() {
		boolean result = true;
		try {
			result = SetupUtils.packageInstalled("localhost", null, "agem5644", null, null, "gedit", "0.0.0");
		} catch (Exception e) {
		}
		assertEquals(false, result);
	}
	
	@Test
	public void packageShouldNotInstalledOnRemote() {
		boolean result = true;
		try {
			result = SetupUtils.packageInstalled("192.168.1.40", "pardus", "agem5644", 22, null, "vim", "0.0.0");
		} catch (Exception e) {
		}
		assertEquals(false, result);
	}
	
}
