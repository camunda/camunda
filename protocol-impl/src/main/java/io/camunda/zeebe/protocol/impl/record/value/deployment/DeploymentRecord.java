/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsMetadataValue;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;

public final class DeploymentRecord extends UnifiedRecordValue implements DeploymentRecordValue {

  public static final String RESOURCES = "resources";
  public static final String PROCESSES = "processesMetadata";

  private final ArrayProperty<DeploymentResource> resourcesProp =
      new ArrayProperty<>(RESOURCES, new DeploymentResource());

  private final ArrayProperty<ProcessMetadata> processesMetadataProp =
      new ArrayProperty<>(PROCESSES, new ProcessMetadata());

  private final ArrayProperty<DecisionRecord> decisionMetadataProp =
      new ArrayProperty<>("decisionsMetadata", new DecisionRecord());

  private final ArrayProperty<DecisionRequirementsMetadataRecord> decisionRequirementsMetadataProp =
      new ArrayProperty<>("decisionRequirementsMetadata", new DecisionRequirementsMetadataRecord());
  private final StringProperty tenantIdProp = new StringProperty("tenantId", "");

  public DeploymentRecord() {
    declareProperty(resourcesProp)
        .declareProperty(processesMetadataProp)
        .declareProperty(decisionRequirementsMetadataProp)
        .declareProperty(decisionMetadataProp)
        .declareProperty(tenantIdProp);
  }

  public ValueArray<ProcessMetadata> processesMetadata() {
    return processesMetadataProp;
  }

  public ValueArray<DeploymentResource> resources() {
    return resourcesProp;
  }

  public ValueArray<DecisionRecord> decisionsMetadata() {
    return decisionMetadataProp;
  }

  public ValueArray<DecisionRequirementsMetadataRecord> decisionRequirementsMetadata() {
    return decisionRequirementsMetadataProp;
  }

  @Override
  public List<io.camunda.zeebe.protocol.record.value.deployment.DeploymentResource> getResources() {
    final List<io.camunda.zeebe.protocol.record.value.deployment.DeploymentResource> resources =
        new ArrayList<>();

    for (final DeploymentResource resource : resourcesProp) {
      final DeploymentResource copiedResource = new DeploymentResource();
      final var copyBuffer = BufferUtil.createCopy(resource);
      copiedResource.wrap(copyBuffer);
      resources.add(copiedResource);
    }

    return resources;
  }

  @Override
  public List<ProcessMetadataValue> getProcessesMetadata() {
    final List<ProcessMetadataValue> processesMeta = new ArrayList<>();

    for (final ProcessMetadata processRecord : processesMetadataProp) {
      final ProcessMetadata copiedProcessRecord = new ProcessMetadata();
      final var copyBuffer = BufferUtil.createCopy(processRecord);
      copiedProcessRecord.wrap(copyBuffer);
      processesMeta.add(copiedProcessRecord);
    }

    return processesMeta;
  }

  @Override
  public List<DecisionRecordValue> getDecisionsMetadata() {
    final var metadataList = new ArrayList<DecisionRecordValue>();

    for (final DecisionRecord metadata : decisionMetadataProp) {
      final var copyRecord = new DecisionRecord();
      final var copyBuffer = BufferUtil.createCopy(metadata);
      copyRecord.wrap(copyBuffer);
      metadataList.add(copyRecord);
    }

    return metadataList;
  }

  @Override
  public List<DecisionRequirementsMetadataValue> getDecisionRequirementsMetadata() {
    final var metadataList = new ArrayList<DecisionRequirementsMetadataValue>();

    for (final DecisionRequirementsMetadataRecord metadata : decisionRequirementsMetadataProp) {
      final var copyRecord = new DecisionRequirementsMetadataRecord();
      final var copyBuffer = BufferUtil.createCopy(metadata);
      copyRecord.wrap(copyBuffer);
      metadataList.add(copyRecord);
    }

    return metadataList;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }
}
