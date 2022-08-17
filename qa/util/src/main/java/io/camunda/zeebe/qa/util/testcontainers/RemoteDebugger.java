/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.testcontainers;

import java.time.Duration;
import org.testcontainers.containers.GenericContainer;

/**
 * To use, create a new instance with the desired port (or, by default, 5005), and pass the
 * container you wish to configure to it.
 *
 * <p>This will start the container and wait for the debugger to attach. To use with Intellij,
 * create a new debug configuration template and pick "Remote JVM Debug". By default, it will be
 * setup for port 5005, which is the default port here as well. You can find out more about this
 * from <a href="https://www.jetbrains.com/help/idea/tutorial-remote-debug.html">this IntelliJ
 * tutorial</a>.
 *
 * <p>This idea came from bsideup's blog, and you can read more about it <a
 * href="https://bsideup.github.io/posts/debugging_containers/">here</a>
 *
 * <p>TODO(npepinpe): document, and add to zeebe-test-container as base capability
 */
public final class RemoteDebugger {
  public static final int DEFAULT_REMOTE_DEBUGGER_PORT = 5005;
  public static final Duration DEFAULT_START_TIMEOUT = Duration.ofMinutes(5);

  private RemoteDebugger() {}

  public static void configureContainer(final GenericContainer<?> container) {
    configureContainer(container, DEFAULT_REMOTE_DEBUGGER_PORT);
  }

  public static void configureContainer(final GenericContainer<?> container, final int port) {
    final var javaOpts = container.getEnvMap().getOrDefault("JAVA_OPTS", "");

    // when configuring a port binding, we need to expose the port as well; the port binding just
    // decides to which host port the exposed port will bind, but it will not expose the port itself
    container.addExposedPort(port);
    container.getPortBindings().add(port + ":" + port);

    // prepend agent configuration in front of javaOpts to ensure it's enabled but also keep
    // previously defined options
    container.withEnv(
        "JAVA_OPTS",
        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=0.0.0.0:"
            + port
            + " "
            + javaOpts);

    container.withStartupTimeout(DEFAULT_START_TIMEOUT);
  }
}
