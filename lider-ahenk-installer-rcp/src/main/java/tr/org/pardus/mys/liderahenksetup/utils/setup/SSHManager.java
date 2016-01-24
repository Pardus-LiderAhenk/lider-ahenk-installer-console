package tr.org.pardus.mys.liderahenksetup.utils.setup;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import tr.org.pardus.mys.liderahenksetup.exception.CommandExecutionException;
import tr.org.pardus.mys.liderahenksetup.exception.SSHConnectionException;
import tr.org.pardus.mys.liderahenksetup.utils.PropertyReader;

/**
 * SSHManager is responsible for managing SSH connections and executing
 * commands.
 * 
 * @author Emre Akkaya <emre.akkaya@agem.com.tr>
 *
 */
public class SSHManager {

	private static final Logger logger = Logger.getLogger(SSHManager.class.getName());

	private JSch SSHChannel;
	private Session session;
	private Properties config;

	private String username;
	private String password;
	private String ip;
	private int port;
	private String privateKey;

	/**
	 * 
	 * @param ip
	 * @param username
	 * @param password
	 * @param port
	 * @param privateKey
	 */
	public SSHManager(String ip, String username, String password, Integer port, String privateKey) {
		init();
		this.ip = ip;
		this.username = username;
		this.password = password;
		this.port = (port == null ? Integer.parseInt(PropertyReader.property("connection.port")) : port);
		this.privateKey = privateKey;
	}

	/**
	 * Initializes SSH manager by configuring encryption algorithms and setting
	 * SSH logger.
	 */
	private void init() {
		JSch.setLogger(new DefaultSSHLogger());
		SSHChannel = new JSch();
		config = new Properties();
		config.put("kex",
				"diffie-hellman-group1-sha1,diffie-hellman-group14-sha1,diffie-hellman-group-exchange-sha1,diffie-hellman-group-exchange-sha256");
		config.put("StrictHostKeyChecking", "no");
	}

	/**
	 * Tries to connect via SSH key or username-password pair
	 * 
	 * @throws SSHConnectionException
	 *             if it fails to connect
	 */
	public void connect() throws SSHConnectionException {
		try {
			if (privateKey != null && !privateKey.isEmpty()) {
				SSHChannel.addIdentity(privateKey); // TODO passphrase
			}
			session = SSHChannel.getSession(username, ip, port);
			if (password != null && !password.isEmpty()) {
				session.setPassword(password);
			}
			session.setConfig(config);
			session.connect(PropertyReader.propertyInt("session.timeout").intValue());
		} catch (JSchException e) {
			logger.log(Level.SEVERE, e.getMessage());
			throw new SSHConnectionException(e.getMessage());
		}
	}

	/**
	 * Executes command string via SSH
	 * 
	 * @param command
	 *            Command String
	 * @param outputStreamProvider
	 *            Provides an array of bytes which is used to pass arguments to
	 *            the command executed.
	 * @return output of the executed command
	 * @throws CommandExecutionException
	 * 
	 */
	public String execCommand(final String command, final IOutputStreamProvider outputStreamProvider)
			throws CommandExecutionException {

		StringBuilder outputBuffer = new StringBuilder();

		logger.log(Level.INFO, "Command: {0}", command);

		try {
			Channel channel = session.openChannel("exec");
			((ChannelExec) channel).setCommand(command);

			// Open channel and handle output stream
			InputStream inputStream = channel.getInputStream();
			((ChannelExec) channel).setPty(true);

			OutputStream outputStream = null;
			byte[] byteArray = null;
			if (outputStreamProvider != null) {
				outputStream = channel.getOutputStream();
				byteArray = outputStreamProvider.getStreamAsByteArray();
			}

			channel.connect(PropertyReader.propertyInt("channel.timeout").intValue());

			// Pass provided byte array as command argument
			if (outputStream != null && byteArray != null) {
				outputStream.write(byteArray);
				outputStream.flush();
			}

			// Read output from the stream and handle the channel accordingly
			byte[] tmp = new byte[1024];
			while (true) {
				while (inputStream.available() > 0) {
					int i = inputStream.read(tmp, 0, 1024);
					if (i < 0)
						break;
					logger.log(Level.INFO, new String(tmp, 0, i));
				}
				if (channel.isClosed()) {
					logger.log(Level.INFO, "exit status: " + channel.getExitStatus());
					break;
				}
				try {
					Thread.sleep(1000);
				} catch (Exception ee) {
				}
			}

			channel.disconnect();

		} catch (Exception e) {
			logger.log(Level.SEVERE, e.getMessage());
			throw new CommandExecutionException(e.getMessage());
		}

		return outputBuffer.toString();
	}

	/**
	 * Executes command string via SSH. Replaces parameter indicators with
	 * values from the provided array before execution.
	 * 
	 * @param command
	 * @param params
	 * @return output of the executed command
	 * @throws CommandExecutionException
	 */
	public String execCommand(final String command, final Object[] params) throws CommandExecutionException {
		return execCommand(command, params, null);
	}

	/**
	 * Executes command string via SSH. Replaces parameter indicators with
	 * values from the provided array before execution. While executing the
	 * command feeds its output stream via IOutputStreamProvider instance
	 * 
	 * @param command
	 * @param params
	 * @param outputStreamProvider
	 * @return output of the executed command
	 * @throws CommandExecutionException
	 */
	public String execCommand(final String command, final Object[] params, IOutputStreamProvider outputStreamProvider)
			throws CommandExecutionException {
		String tmpCommand = command;
		if (params != null) {
			for (int i = 0; i < params.length; i++) {
				String param = params[i].toString();
				tmpCommand = tmpCommand.replaceAll("\\{" + i + "\\}", param);
			}
		}
		return execCommand(tmpCommand, outputStreamProvider);
	}

	/**
	 * Tries to safe-copy provided local file to remote server.
	 * 
	 * @param fileToTransfer
	 * @param destDirectory
	 * @param preserveTimestamp
	 * @throws CommandExecutionException
	 */
	public void copyFileToRemote(final File fileToTransfer, final String destDirectory, final boolean preserveTimestamp)
			throws CommandExecutionException {

		FileInputStream fis = null;
		String error = null;

		try {

			String command = "scp " + (preserveTimestamp ? "-p" : "") + " -t " + destDirectory
					+ fileToTransfer.getName();

			logger.log(Level.INFO, "Command: {0}", command);

			Channel channel = session.openChannel("exec");
			((ChannelExec) channel).setCommand(command);

			OutputStream out = channel.getOutputStream();
			InputStream in = channel.getInputStream();

			channel.connect(PropertyReader.propertyInt("channel.timeout").intValue());

			if ((error = checkAck(in)) != null) {
				throw new CommandExecutionException(error);
			}

			if (preserveTimestamp) {
				command = "T " + (fileToTransfer.lastModified() / 1000) + " 0";
				// The access time should be sent here,
				// but it is not accessible with JavaAPI ;-<
				command += (" " + (fileToTransfer.lastModified() / 1000) + " 0\n");
				out.write(command.getBytes());
				out.flush();
				if ((error = checkAck(in)) != null) {
					throw new CommandExecutionException(error);
				}
			}

			// send scp command
			long filesize = fileToTransfer.length();
			command = "C0644 " + filesize + " " + fileToTransfer.getName() + "\n";
			out.write(command.getBytes());
			out.flush();
			if ((error = checkAck(in)) != null) {
				throw new CommandExecutionException(error);
			}

			// send content of local file
			fis = new FileInputStream(fileToTransfer);
			byte[] buf = new byte[1024];
			while (true) {
				int len = fis.read(buf, 0, buf.length);
				if (len <= 0)
					break;
				out.write(buf, 0, len); // out.flush();
			}
			fis.close();
			fis = null;
			// send '\0'
			buf[0] = 0;
			out.write(buf, 0, 1);
			out.flush();
			if ((error = checkAck(in)) != null) {
				throw new CommandExecutionException(error);
			}
			out.close();

			channel.disconnect();

		} catch (Exception e) {
			logger.log(Level.SEVERE, e.getMessage());
			throw new CommandExecutionException(e.getMessage());
		}

	}

	private static String checkAck(final InputStream in) throws IOException {
		int b = in.read();
		// b may be 0 for success,
		// 1 for error,
		// 2 for fatal error,
		// -1
		if (b == 1 || b == 2) {
			StringBuffer sb = new StringBuffer();
			int c;
			do {
				c = in.read();
				sb.append((char) c);
			} while (c != '\n');
			if (b == 1 || b == 2) { // error
				return sb.toString();
			}
		}
		return null;
	}

	public void disconnect() {
		session.disconnect();
	}

	public JSch getSSHChannel() {
		return SSHChannel;
	}

	public Session getSession() {
		return session;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}

	public String getIp() {
		return ip;
	}

	public int getPort() {
		return port;
	}

	public String getPrivateKey() {
		return privateKey;
	}

}
