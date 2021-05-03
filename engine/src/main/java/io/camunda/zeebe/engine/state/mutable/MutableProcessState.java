/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.mutable;

import io.zeebe.engine.state.immutable.ProcessState;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import org.agrona.DirectBuffer;

public interface MutableProcessState extends ProcessState {

  void putDeployment(DeploymentRecord deploymentRecord);

  void putLatestVersionDigest(DirectBuffer processId, DirectBuffer digest);

  void putProcess(long key, ProcessRecord value);
}
