/*******************************************************************************
 * Copyright (c) 2009, 2013 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Marc R. Hoffmann - initial API and implementation
 *    
 *******************************************************************************/
package org.jacoco.agent.rt.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;

import javax.management.ObjectName;
import javax.management.StandardMBean;

import org.jacoco.agent.rt.IAgent;
import org.jacoco.agent.rt.internal.output.FileOutput;
import org.jacoco.agent.rt.internal.output.IAgentOutput;
import org.jacoco.agent.rt.internal.output.NoneOutput;
import org.jacoco.agent.rt.internal.output.TcpClientOutput;
import org.jacoco.agent.rt.internal.output.TcpServerOutput;
import org.jacoco.agent.rt.internal.core.JaCoCo;
import org.jacoco.agent.rt.internal.core.data.ExecutionDataWriter;
import org.jacoco.agent.rt.internal.core.runtime.AbstractRuntime;
import org.jacoco.agent.rt.internal.core.runtime.AgentOptions;
import org.jacoco.agent.rt.internal.core.runtime.AgentOptions.OutputMode;
import org.jacoco.agent.rt.internal.core.runtime.RuntimeData;

/**
 * The agent manages the life cycle of JaCoCo runtime.
 */
public class Agent implements IAgent {

	private static final String JMX_NAME = "org.jacoco:type=Runtime";

	private static Agent singleton;

	/**
	 * Returns a global instance which is already started. If the method is
	 * called the first time the instance is created with the given options.
	 * 
	 * @param options
	 *            options to configure the instance
	 * @return global instance
	 */
	public static synchronized Agent getInstance(final AgentOptions options) {
		if (singleton == null) {
			final Agent agent = new Agent(options, IExceptionLogger.SYSTEM_ERR);
			agent.startup();
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					agent.shutdown();
				}
			});
			singleton = agent;
		}
		return singleton;
	}

	/**
	 * Returns a global instance which is already started. If a agent has not
	 * been initialized before this method will fail.
	 * 
	 * @return global instance
	 * @throws IllegalStateException
	 *             if no Agent has been started yet
	 */
	public static synchronized Agent getInstance() throws IllegalStateException {
		if (singleton == null) {
			throw new IllegalStateException("JaCoCo agent not started.");
		}
		return singleton;
	}

	private final AgentOptions options;

	private final IExceptionLogger logger;

	private IAgentOutput output;

	private final RuntimeData data;

	/**
	 * Creates a new agent with the given agent options.
	 * 
	 * @param options
	 *            agent options
	 * @param logger
	 *            logger used by this agent
	 */
	Agent(final AgentOptions options, final IExceptionLogger logger) {
		this.options = options;
		this.logger = logger;
		this.data = new RuntimeData();
	}

	/**
	 * Returns the runtime data object created by this agent
	 * 
	 * @return runtime data for this agent instance
	 */
	public RuntimeData getData() {
		return data;
	}

	/**
	 * Initializes this agent.
	 * 
	 */
	public void startup() {
		
		System.out.println("Startup");
		try {
			String sessionId = options.getSessionId();
			if (sessionId == null) {
				sessionId = createSessionId();
			}
			data.setSessionId(sessionId);
			output = createAgentOutput();
			output.startup(options, data);
			if (options.getJmx()) {
				ManagementFactory.getPlatformMBeanServer().registerMBean(
						new StandardMBean(this, IAgent.class),
						new ObjectName(JMX_NAME));
			}
		} catch (final Exception e) {
			logger.logExeption(e);
		}
	}

	/**
	 * Shutdown the agent again.
	 */
	public void shutdown() {
		System.out.println("shutdown");

		try {
			//if (options.getDumpOnExit()) {
				output.writeExecutionData(false);
			//}
			output.shutdown();
			if (options.getJmx()) {
				ManagementFactory.getPlatformMBeanServer().unregisterMBean(
						new ObjectName(JMX_NAME));
			}
		} catch (final Exception e) {
			logger.logExeption(e);
		}
	}

	/**
	 * Create output implementation as given by the agent options.
	 * 
	 * @return configured controller implementation
	 */
	IAgentOutput createAgentOutput() {
		final OutputMode controllerType = options.getOutput();
		switch (controllerType) {
		case file:
			return new FileOutput();
		case tcpserver:
			return new TcpServerOutput(logger);
		case tcpclient:
			return new TcpClientOutput(logger);
		case none:
			return new NoneOutput();
		default:
			throw new AssertionError(controllerType);
		}
	}

	private String createSessionId() {
		String host;
		try {
			host = InetAddress.getLocalHost().getHostName();
		} catch (final Exception e) {
			// Also catch platform specific exceptions (like on Android) to
			// avoid bailing out here
			host = "unknownhost";
		}
		return host + "-" + AbstractRuntime.createRandomId();
	}

	// === IAgent Implementation ===

	public String getVersion() {
		return JaCoCo.VERSION;
	}

	public String getSessionId() {
		return data.getSessionId();
	}

	public void setSessionId(final String id) {
		data.setSessionId(id);
	}

	public void reset() {
		data.reset();
	}

	public byte[] getExecutionData(final boolean reset) {
		System.out.println("dumping");
		final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try {
			final ExecutionDataWriter writer = new ExecutionDataWriter(buffer);
			data.collect(writer, writer, reset);
		} catch (final IOException e) {
			// Must not happen with ByteArrayOutputStream
			throw new AssertionError(e);
		}
		return buffer.toByteArray();
	}

	public void dump(final boolean reset) throws IOException {
		System.out.println("dumping");
		output.writeExecutionData(reset);
	}

}
