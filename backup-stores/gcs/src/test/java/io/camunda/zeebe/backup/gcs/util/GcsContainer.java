/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.gcs.util;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

public final class GcsContainer extends GenericContainer<GcsContainer> {
  private static final DockerImageName IMAGE = DockerImageName.parse("fsouza/fake-gcs-server");
  private static final int PORT = 4443;
  private static final String STARTER_SCRIPT = "/testcontainers_start.sh";

  public GcsContainer() {
    this("1");
  }

  public GcsContainer(String version) {
    super(IMAGE.withTag(version));
    this.withExposedPorts(PORT)
        .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("sh"))
        .withCommand(
            "-c", "while [ ! -f " + STARTER_SCRIPT + " ]; do sleep 0.1; done; " + STARTER_SCRIPT);
  }

  @Override
  protected void containerIsStarting(final InspectContainerResponse containerInfo) {
    super.containerIsStarting(containerInfo);
    //noinspection OctalInteger
    copyFileToContainer(
        Transferable.of(
            "/bin/fake-gcs-server -data /data -scheme http -external-url %s"
                .formatted(externalEndpoint()),
            0777),
        STARTER_SCRIPT);
  }

  @SuppressWarnings("HttpUrlsUsage")
  public String externalEndpoint() {
    return "http://%s:%d".formatted(getHost(), getMappedPort(PORT));
  }
}
