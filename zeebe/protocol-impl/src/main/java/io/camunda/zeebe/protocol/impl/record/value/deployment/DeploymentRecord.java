/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsMetadataValue;
import io.camunda.zeebe.protocol.record.value.deployment.FormMetadataValue;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;

public final class DeploymentRecord extends UnifiedRecordValue implements DeploymentRecordValue {

  public static final String RESOURCES = "resources";
  public static final String PROCESSES = "processesMetadata";

  private final ArrayProperty<DeploymentResource> resourcesProp =
      new ArrayProperty<>(RESOURCES, DeploymentResource::new);

  private final ArrayProperty<ProcessMetadata> processesMetadataProp =
      new ArrayProperty<>(PROCESSES, ProcessMetadata::new);

  private final ArrayProperty<DecisionRecord> decisionMetadataProp =
      new ArrayProperty<>("decisionsMetadata", DecisionRecord::new);

  private final ArrayProperty<DecisionRequirementsMetadataRecord> decisionRequirementsMetadataProp =
      new ArrayProperty<>("decisionRequirementsMetadata", DecisionRequirementsMetadataRecord::new);

  private final ArrayProperty<FormMetadataRecord> formMetadataProp =
      new ArrayProperty<>("formMetadata", FormMetadataRecord::new);

  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public DeploymentRecord() {
    super(6);
    declareProperty(resourcesProp)
        .declareProperty(processesMetadataProp)
        .declareProperty(decisionRequirementsMetadataProp)
        .declareProperty(decisionMetadataProp)
        .declareProperty(formMetadataProp)
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

  public ValueArray<FormMetadataRecord> formMetadata() {
    return formMetadataProp;
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
  public List<FormMetadataValue> getFormMetadata() {
    final var metadataList = new ArrayList<FormMetadataValue>();

    for (final FormMetadataRecord metadata : formMetadataProp) {
      final var copyRecord = new FormMetadataRecord();
      final var copyBuffer = BufferUtil.createCopy(metadata);
      copyRecord.wrap(copyBuffer);
      metadataList.add(copyRecord);
    }

    return metadataList;
  }

  public void resetResources() {
    resourcesProp.reset();
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public DeploymentRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  public boolean hasBpmnResources() {
    return getResources().stream()
        .map(io.camunda.zeebe.protocol.record.value.deployment.DeploymentResource::getResourceName)
        .anyMatch(x -> x.endsWith(".bpmn") || x.endsWith(".xml"));
  }

  public boolean hasDmnResources() {
    return getResources().stream()
        .map(io.camunda.zeebe.protocol.record.value.deployment.DeploymentResource::getResourceName)
        .anyMatch(x -> x.endsWith(".dmn"));
  }

  public boolean hasForms() {
    return getResources().stream()
        .map(io.camunda.zeebe.protocol.record.value.deployment.DeploymentResource::getResourceName)
        .anyMatch(x -> x.endsWith(".form"));
  }

  public boolean hasDuplicatesOnly() {
    return processesMetadata().stream().allMatch(ProcessMetadata::isDuplicate)
        && decisionRequirementsMetadata().stream()
            .allMatch(DecisionRequirementsMetadataValue::isDuplicate)
        && formMetadata().stream().allMatch(FormMetadataValue::isDuplicate);
  }
}
