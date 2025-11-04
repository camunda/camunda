/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import io.camunda.security.reader.ResourceAccessController;

public class CamundaWriteClients implements WriteClientsProxy {

  private final HistoryDeletionWriteClient historyDeletionWriteClient;
  private final ResourceAccessController resourceAccessController;

  public CamundaWriteClients(
      final HistoryDeletionWriteClient historyDeletionWriteClient,
      final ResourceAccessController resourceAccessController) {
    this.historyDeletionWriteClient = historyDeletionWriteClient;
    this.resourceAccessController = resourceAccessController;
  }

  @Override
  public void deleteHistoricData(final long processInstanceKey) {
    historyDeletionWriteClient.deleteHistoricData(processInstanceKey);
  }
}
