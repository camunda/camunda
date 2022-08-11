/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.protocol.record.RecordValueWithTenant;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import org.agrona.DirectBuffer;

public interface ProcessState {

  default DeployedProcess getLatestProcessVersionByProcessId(final DirectBuffer processId) {
    return getLatestProcessVersionByProcessId(
        BufferUtil.wrapString(RecordValueWithTenant.DEFAULT_TENANT_ID), processId);
  }

  DeployedProcess getLatestProcessVersionByProcessId(
      final DirectBuffer tenantId, DirectBuffer processId);

  default DeployedProcess getProcessByProcessIdAndVersion(
      final DirectBuffer processId, final int version) {
    return getProcessByProcessIdAndVersion(
        BufferUtil.wrapString(RecordValueWithTenant.DEFAULT_TENANT_ID), processId, version);
  }

  DeployedProcess getProcessByProcessIdAndVersion(
      final DirectBuffer tenantId, DirectBuffer processId, int version);

  DeployedProcess getProcessByKey(long key);

  Collection<DeployedProcess> getProcesses();

  default Collection<DeployedProcess> getProcessesByBpmnProcessId(
      final DirectBuffer bpmnProcessId) {
    return getProcessesByBpmnProcessId(
        BufferUtil.wrapString(RecordValueWithTenant.DEFAULT_TENANT_ID), bpmnProcessId);
  }

  Collection<DeployedProcess> getProcessesByBpmnProcessId(
      final DirectBuffer tenantId, DirectBuffer bpmnProcessId);

  default DirectBuffer getLatestVersionDigest(final DirectBuffer processId) {
    return getLatestVersionDigest(
        BufferUtil.wrapString(RecordValueWithTenant.DEFAULT_TENANT_ID), processId);
  }

  DirectBuffer getLatestVersionDigest(final DirectBuffer tenantId, DirectBuffer processId);

  default int getProcessVersion(final String bpmnProcessId) {
    return getProcessVersion(RecordValueWithTenant.DEFAULT_TENANT_ID, bpmnProcessId);
  }

  int getProcessVersion(final String tenantId, String bpmnProcessId);

  <T extends ExecutableFlowElement> T getFlowElement(
      long processDefinitionKey, DirectBuffer elementId, Class<T> elementType);
}
