/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.testcontainers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Testcontainers wrapper for <a
 * href="https://learn.microsoft.com/en-us/azure/storage/common/storage-use-azurite">Azurite</a>
 * (Azure Blob Storage emulator).
 *
 * <p>Uses the well-known Azurite development credentials documented at <a
 * href="https://learn.microsoft.com/en-us/azure/storage/common/storage-use-azurite?tabs=visual-studio%2Cblob-storage#well-known-storage-account-and-key">Microsoft
 * Docs</a>.
 *
 * <p>When Docker networking is needed (e.g. for container-to-container access), call {@link
 * #withNetworkAlias(String)} to set up an alias and use {@link #internalConnectionString()} for the
 * connection string visible from within the Docker network.
 */
public final class AzuriteContainer extends GenericContainer<AzuriteContainer> {

  private static final int BLOB_PORT = 10000;
  private static final String ACCOUNT_NAME = "devstoreaccount1";
  private static final String ACCOUNT_KEY =
      "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq"
          + "/K1SZFPTOtr/KBHBeksoGMGw==";

  private String networkAlias;

  public AzuriteContainer() {
    super("mcr.microsoft.com/azure-storage/azurite");
    withExposedPorts(BLOB_PORT);
    withCommand("azurite-blob", "--blobHost", "0.0.0.0");
    waitingFor(
        Wait.forLogMessage(
            ".*Azurite Blob service successfully listens on http://0.0.0.0:10000.*\n", 1));
  }

  /**
   * Sets a network alias so that other containers on the same Docker network can reach this Azurite
   * instance. Use {@link #internalConnectionString()} to get the connection string that resolves
   * from within the Docker network.
   */
  public AzuriteContainer withNetworkAlias(final String alias) {
    networkAlias = alias;
    withNetworkAliases(alias);
    return this;
  }

  /**
   * Returns a connection string for host-side access (via the mapped port).
   *
   * <p>This is equivalent to the legacy {@link #getConnectString()} method.
   */
  public String externalConnectionString() {
    return connectionString(getHost(), getMappedPort(BLOB_PORT));
  }

  /**
   * Returns a connection string for container-to-container access (via the network alias). Only
   * available when a network alias has been set via {@link #withNetworkAlias(String)}.
   *
   * @throws IllegalStateException if no network alias has been configured
   */
  public String internalConnectionString() {
    if (networkAlias == null) {
      throw new IllegalStateException(
          "No network alias configured — call withNetworkAlias(String) first");
    }
    return connectionString(networkAlias, BLOB_PORT);
  }

  /**
   * Returns a connection string using the mapped external port.
   *
   * @deprecated Use {@link #externalConnectionString()} instead.
   */
  @Deprecated
  public String getConnectString() {
    return externalConnectionString();
  }

  private static String connectionString(final String host, final int port) {
    return "DefaultEndpointsProtocol=http"
        + ";AccountName="
        + ACCOUNT_NAME
        + ";AccountKey="
        + ACCOUNT_KEY
        + ";BlobEndpoint=http://"
        + host
        + ":"
        + port
        + "/"
        + ACCOUNT_NAME
        + ";";
  }
}
