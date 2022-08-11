/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.RecordValueWithTenant;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public interface MutableProcessState extends ProcessState {

  void putDeployment(DeploymentRecord deploymentRecord);

  void putLatestVersionDigest(
      final DirectBuffer tenantId, DirectBuffer processId, DirectBuffer digest);

  default void putLatestVersionDigest(final DirectBuffer processId, final DirectBuffer digest) {
    putLatestVersionDigest(
        BufferUtil.wrapString(RecordValueWithTenant.DEFAULT_TENANT_ID), processId, digest);
  }

  void putProcess(long key, ProcessRecord value);
}
