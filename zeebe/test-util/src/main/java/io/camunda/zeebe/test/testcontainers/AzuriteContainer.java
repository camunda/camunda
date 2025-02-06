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

public class AzuriteContainer extends GenericContainer<AzuriteContainer> {

  private static final String CONNECTION_STRING_PREFIX =
      "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;"
          + "AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;"
          + "BlobEndpoint=http://127.0.0.1:";
  private static final String CONNECTION_STRING_SUFIX = "/devstoreaccount1;";
  private static final GenericContainer AZURITE_CONTAINER =
      new GenericContainer("mcr.microsoft.com/azure-storage/azurite")
          .withExposedPorts(10000)
          .waitingFor(
              Wait.forLogMessage(
                  ".*Azurite Blob service successfully listens on http://0.0.0.0:10000*\n", 1))
          .withCommand("azurite-blob --blobHost 0.0.0.0");
  //  Explanation on how to connect with the azurite container:
  // https://learn.microsoft.com/en-us/azure/storage/common/storage-use-azurite?tabs=visual-studio%2Cblob-storage#well-known-storage-account-and-key
  public String connectStr;

  public AzuriteContainer() {
    AZURITE_CONTAINER.start();
    final int actualPort = AZURITE_CONTAINER.getMappedPort(10000);
    connectStr = CONNECTION_STRING_PREFIX + actualPort + CONNECTION_STRING_SUFIX;
  }

  public String getConnectString() {
    return connectStr;
  }
}
