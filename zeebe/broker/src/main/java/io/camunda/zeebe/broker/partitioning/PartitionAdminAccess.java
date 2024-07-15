/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import io.camunda.zeebe.broker.system.configuration.FlowControlCfg;
import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControlLimits;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.Optional;

public interface PartitionAdminAccess {
  Optional<PartitionAdminAccess> forPartition(int partitionId);

  ActorFuture<Void> takeSnapshot();

  ActorFuture<Void> pauseExporting();

  ActorFuture<Void> softPauseExporting();

  ActorFuture<Void> resumeExporting();

  ActorFuture<Void> pauseProcessing();

  ActorFuture<Void> resumeProcessing();

  ActorFuture<Void> banInstance(final long processInstanceKey);

  ActorFuture<Void> configureFlowControl(final FlowControlCfg flowControlCfg);

  ActorFuture<FlowControlLimits> getFlowControlConfiguration();
}
