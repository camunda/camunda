/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.gcp.util;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers wrapper around a community GCP Secret Manager emulator, mirroring the approach the
 * GCS backup store takes with {@code fsouza/fake-gcs-server}. GCP ships no official Secret Manager
 * emulator, so this uses the {@code blackwell-systems} image, which implements the full Secret
 * Manager gRPC API without requiring credentials.
 *
 * <p>The image is a third-party community image, so it is pinned by immutable {@code @sha256}
 * digest rather than a floating tag: the digest is tamper-evident and guarantees a reproducible CI
 * pull even if the {@code 1.9} tag is later moved or deleted upstream. When bumping the emulator
 * version, update the digest and the {@code 1.9} reference in the comment below together.
 */
public final class SecretManagerEmulatorContainer
    extends GenericContainer<SecretManagerEmulatorContainer> {

  // ghcr.io/blackwell-systems/gcp-secret-manager-emulator-dual:1.9 (multi-arch index digest)
  private static final DockerImageName IMAGE =
      DockerImageName.parse(
          "ghcr.io/blackwell-systems/gcp-secret-manager-emulator-dual"
              + "@sha256:2c9d3bb0453fdff102b29af00b4abd9b2c6fa466031a9d8e6d93a270ba22a6e2");
  private static final int GRPC_PORT = 9090;

  public SecretManagerEmulatorContainer() {
    super(IMAGE);
    withExposedPorts(GRPC_PORT)
        .waitingFor(Wait.forLogMessage(".*Ready to accept both gRPC and REST requests.*", 1));
  }

  /** The {@code host:port} endpoint of the emulator's plaintext gRPC server. */
  public String grpcEndpoint() {
    return "%s:%d".formatted(getHost(), getMappedPort(GRPC_PORT));
  }
}
