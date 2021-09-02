/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import io.camunda.zeebe.broker.system.partitions.impl.StateControllerImpl;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import java.util.List;

public interface PartitionTransitionContext extends PartitionContext {

  int getNodeId();

  LogStream getLogStream();

  AsyncSnapshotDirector getSnapshotDirector();

  StateControllerImpl getStateController();

  ConstructableSnapshotStore getConstructableSnapshotStore();

  List<PartitionListener> getPartitionListeners();

  PartitionContext getPartitionContext();
}
