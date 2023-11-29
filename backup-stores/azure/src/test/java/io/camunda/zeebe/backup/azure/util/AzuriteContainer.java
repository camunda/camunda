/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.azure.util;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class AzuriteContainer extends GenericContainer<AzuriteContainer> {

  public static String connectStr;
  private static final String CONNECTION_STRING_PREFIX =
      "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;"
          + "AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;"
          + "BlobEndpoint=http://127.0.0.1:";
  private static final String CONNECTION_STRING_SUFIX = "/devstoreaccount1;";
  private static final GenericContainer AZURITE_CONTAINER =
      new GenericContainer("mcr.microsoft.com/azure-storage/azurite")
          .withExposedPorts(10000)
          .withCommand("azurite-blob --blobHost 0.0.0.0");

  public AzuriteContainer() {
    AZURITE_CONTAINER.start();
    AZURITE_CONTAINER.waitingFor(Wait.forHttp("/"));
    final int actualPort = AZURITE_CONTAINER.getMappedPort(10000);
    connectStr = CONNECTION_STRING_PREFIX + actualPort + CONNECTION_STRING_SUFIX;
  }

  public String getConnectStr() {
    return connectStr;
  }
}
