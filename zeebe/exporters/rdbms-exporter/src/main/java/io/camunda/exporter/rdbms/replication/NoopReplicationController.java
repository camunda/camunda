/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.replication;

import io.camunda.zeebe.exporter.api.context.Controller;

public class NoopReplicationController implements ReplicationController {

  private final Controller controller;

  public NoopReplicationController(final Controller controller) {
    this.controller = controller;
  }

  @Override
  public void onFlush(final long exporterPosition) {
    controller.updateLastExportedRecordPosition(exporterPosition);
  }

  @Override
  public void close() throws Exception {
    // noop
  }
}
