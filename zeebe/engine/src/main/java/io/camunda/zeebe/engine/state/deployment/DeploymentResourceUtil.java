/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import io.camunda.zeebe.engine.processing.deployment.ChecksumGenerator;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsMetadataRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormMetadataRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessMetadata;
import io.camunda.zeebe.util.buffer.BufferUtil;

public final class DeploymentResourceUtil {
  private final ChecksumGenerator checksumGenerator = new ChecksumGenerator();

  public void applyProcessMetadata(final PersistedProcess source, final ProcessMetadata target) {
    target.setKey(source.getKey());
    target.setBpmnProcessId(source.getBpmnProcessId());
    target.setResourceName(source.getResourceName());
    target.setVersion(source.getVersion());
    target.setVersionTag(source.getVersionTag());
    target.setTenantId(source.getTenantId());
    target.setDeploymentKey(source.getDeploymentKey());
    target.setChecksum(checksumGenerator.checksum(source.getResource()));
  }

  public void applyFormMetadata(final PersistedForm source, final FormMetadataRecord target) {
    target.setFormKey(source.getFormKey());
    target.setFormId(BufferUtil.bufferAsString(source.getFormId()));
    target.setResourceName(BufferUtil.bufferAsString(source.getResourceName()));
    target.setVersion(source.getVersion());
    target.setVersionTag(source.getVersionTag());
    target.setTenantId(source.getTenantId());
    target.setDeploymentKey(source.getDeploymentKey());
    target.setChecksum(checksumGenerator.checksum(source.getResource()));
  }

  public void applyDecisionMetadata(final PersistedDecision source, final DecisionRecord target) {
    target.setDecisionKey(source.getDecisionKey());
    target.setDecisionId(BufferUtil.bufferAsString(source.getDecisionId()));
    target.setDecisionName(BufferUtil.bufferAsString(source.getDecisionName()));
    target.setVersion(source.getVersion());
    target.setDecisionRequirementsKey(source.getDecisionRequirementsKey());
    target.setDecisionRequirementsId(BufferUtil.bufferAsString(source.getDecisionRequirementsId()));
    target.setTenantId(source.getTenantId());
    target.setDeploymentKey(source.getDeploymentKey());
  }

  public void applyDecisionRequirementsMetadata(
      final PersistedDecisionRequirements source, final DecisionRequirementsMetadataRecord target) {
    target.setDecisionRequirementsKey(source.getDecisionRequirementsKey());
    target.setDecisionRequirementsId(BufferUtil.bufferAsString(source.getDecisionRequirementsId()));
    target.setDecisionRequirementsName(
        BufferUtil.bufferAsString(source.getDecisionRequirementsName()));
    target.setResourceName(BufferUtil.bufferAsString(source.getResourceName()));
    target.setTenantId(source.getTenantId());
    target.setChecksum(checksumGenerator.checksum(source.getResource()));
  }
}
