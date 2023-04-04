/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.testcontainers;

import java.util.List;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

public final class GcsContainer extends GenericContainer<GcsContainer> {
  private static final DockerImageName IMAGE = DockerImageName.parse("fsouza/fake-gcs-server");
  private static final String IMAGE_TAG = "1";
  private static final int PORT = 4443;
  private final String domain;

  public GcsContainer(final Network network, final String domain) {
    super(IMAGE.withTag(IMAGE_TAG));
    this.domain = domain;
    setCommand("-scheme", "http", "-external-url", internalEndpoint());
    setExposedPorts(List.of(PORT));
    setNetworkAliases(List.of(domain));
    setNetwork(network);
  }

  public String internalEndpoint() {
    return "http://" + domain + ":" + PORT;
  }

  public String externalEndpoint() {
    return "http://%s:%d".formatted(getHost(), getMappedPort(PORT));
  }
}
