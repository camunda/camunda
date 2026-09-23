/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.managedscriptdefinition;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.value.ManagedScriptDefinitionStatus;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.junit.jupiter.api.Test;

final class ManagedScriptDefinitionRecordTest {

  @Test
  void shouldCopyAllFields() {
    // given
    final var original =
        new ManagedScriptDefinitionRecord()
            .setManagedScriptDefinitionKey(1L)
            .setStatus(ManagedScriptDefinitionStatus.PENDING)
            .setRevision(5L)
            .setResourceKey(2L)
            .setResourceName("script.js")
            .setArtifactDigest(BufferUtil.wrapString("digest"))
            .setElementId("script-task")
            .setBpmnProcessId("process")
            .setProcessDefinitionKey(3L)
            .setProcessDefinitionVersion(4)
            .setProcessDefinitionVersionTag("v1")
            .setLanguage("javascript")
            .setRuntime("nodejs22")
            .setProvider("fake")
            .setLeaseOwner("worker")
            .setLeaseToken("lease")
            .setLeaseExpiresAt(1000L)
            .setLeaseDuration(500L)
            .setProviderOperationId("operation")
            .setProviderDeploymentId("deployment")
            .setFailureCode("code")
            .setFailureMessage("message")
            .setRetryable(true)
            .setOperationId("request")
            .setTenantId("tenant");
    final var copy = new ManagedScriptDefinitionRecord();

    // when
    copy.copyFrom(original);

    // then
    assertThat(copy.getManagedScriptDefinitionKey())
        .isEqualTo(original.getManagedScriptDefinitionKey());
    assertThat(copy.getStatus()).isEqualTo(original.getStatus());
    assertThat(copy.getRevision()).isEqualTo(original.getRevision());
    assertThat(copy.getResourceKey()).isEqualTo(original.getResourceKey());
    assertThat(copy.getResourceName()).isEqualTo(original.getResourceName());
    assertThat(copy.getArtifactDigest()).containsExactly(original.getArtifactDigest());
    assertThat(copy.getElementId()).isEqualTo(original.getElementId());
    assertThat(copy.getBpmnProcessId()).isEqualTo(original.getBpmnProcessId());
    assertThat(copy.getProcessDefinitionKey()).isEqualTo(original.getProcessDefinitionKey());
    assertThat(copy.getProcessDefinitionVersion())
        .isEqualTo(original.getProcessDefinitionVersion());
    assertThat(copy.getProcessDefinitionVersionTag())
        .isEqualTo(original.getProcessDefinitionVersionTag());
    assertThat(copy.getLanguage()).isEqualTo(original.getLanguage());
    assertThat(copy.getRuntime()).isEqualTo(original.getRuntime());
    assertThat(copy.getProvider()).isEqualTo(original.getProvider());
    assertThat(copy.getLeaseOwner()).isEqualTo(original.getLeaseOwner());
    assertThat(copy.getLeaseToken()).isEqualTo(original.getLeaseToken());
    assertThat(copy.getLeaseExpiresAt()).isEqualTo(original.getLeaseExpiresAt());
    assertThat(copy.getLeaseDuration()).isEqualTo(original.getLeaseDuration());
    assertThat(copy.getProviderOperationId()).isEqualTo(original.getProviderOperationId());
    assertThat(copy.getProviderDeploymentId()).isEqualTo(original.getProviderDeploymentId());
    assertThat(copy.getFailureCode()).isEqualTo(original.getFailureCode());
    assertThat(copy.getFailureMessage()).isEqualTo(original.getFailureMessage());
    assertThat(copy.isRetryable()).isEqualTo(original.isRetryable());
    assertThat(copy.getOperationId()).isEqualTo(original.getOperationId());
    assertThat(copy.getTenantId()).isEqualTo(original.getTenantId());
  }
}
