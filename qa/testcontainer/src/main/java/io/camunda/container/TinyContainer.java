/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.container;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A tiny container which simply starts an interactive, long living alpine with basic busybox
 * utilities. Useful as a file copy container, or archiving container, etc.
 */
public final class TinyContainer extends GenericContainer<TinyContainer> {
  private static final DockerImageName IMAGE = DockerImageName.parse("alpine:3.14.2");

  /** Configures a new container using an Alpine based image */
  public TinyContainer() {
    super(IMAGE);
  }

  @SuppressWarnings("resource")
  @Override
  protected void configure() {
    super.configure();
    withCommand("cat").withCreateContainerCmdModifier(cmd -> cmd.withTty(true));
  }
}
