/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning;

import java.util.concurrent.CompletableFuture;

public interface PartitionAdminAccess {

  CompletableFuture<Void> takeSnapshots();

  CompletableFuture<Void> pauseExporting();

  CompletableFuture<Void> resumeExporting();

  CompletableFuture<Void> pauseProcessing();

  CompletableFuture<Void> resumeProcessing();
}
