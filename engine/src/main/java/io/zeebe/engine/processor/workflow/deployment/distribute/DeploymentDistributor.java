/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.distribute;

import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.DirectBuffer;

public interface DeploymentDistributor {
  ActorFuture<Void> pushDeployment(long key, long position, DirectBuffer buffer);

  PendingDeploymentDistribution removePendingDeployment(long key);
}
