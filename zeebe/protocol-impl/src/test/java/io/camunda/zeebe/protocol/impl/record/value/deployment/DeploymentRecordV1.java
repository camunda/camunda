/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.agentdefinition.AgentDefinitionRecordV1;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsMetadataValue;
import io.camunda.zeebe.protocol.record.value.deployment.FormMetadataValue;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.protocol.record.value.deployment.ResourceMetadataValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class DeploymentRecordV1 extends UnifiedRecordValue implements DeploymentRecordValue {

  public static final String RESOURCES = "resources";
  public static final String PROCESSES = "processesMetadata";

  private final ArrayProperty<DeploymentResource> resourcesProp =
      new ArrayProperty<>(RESOURCES, DeploymentResource::new);

  private final ArrayProperty<ProcessMetadataV1> processesMetadataProp =
      new ArrayProperty<>(PROCESSES, ProcessMetadataV1::new);

  private final ArrayProperty<DecisionRecordV1> decisionMetadataProp =
      new ArrayProperty<>("decisionsMetadata", DecisionRecordV1::new);

  private final ArrayProperty<DecisionRequirementsMetadataRecordV1>
      decisionRequirementsMetadataProp =
          new ArrayProperty<>(
              "decisionRequirementsMetadata", DecisionRequirementsMetadataRecordV1::new);

  private final ArrayProperty<FormMetadataRecordV1> formMetadataProp =
      new ArrayProperty<>("formMetadata", FormMetadataRecordV1::new);

  private final ArrayProperty<ResourceMetadataRecordV1> resourceMetadataProp =
      new ArrayProperty<>("resourceMetadata", ResourceMetadataRecordV1::new);

  /**
   * Agent definitions created at deploy time for elements carrying a recognized agent marker (see
   * {@code AgentElementTypeTransformer}). Internal-only: unlike the other metadata arrays, this is
   * not exposed on the public {@link DeploymentRecordValue} interface, since agent definitions are
   * a deploy-time engine concern rather than a deployment result reported to clients.
   */
  private final ArrayProperty<AgentDefinitionRecordV1> agentDefinitionsMetadataProp =
      new ArrayProperty<>("agentDefinitionsMetadata", AgentDefinitionRecordV1::new);

  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  private final LongProperty deploymentKeyProp = new LongProperty("deploymentKey", -1);

  private final LongProperty reconstructionKeyProp = new LongProperty("reconstructionKey", -1);

  public DeploymentRecordV1() {
    super(9);
    declareProperty(resourcesProp)
        .declareProperty(processesMetadataProp)
        .declareProperty(decisionRequirementsMetadataProp)
        .declareProperty(decisionMetadataProp)
        .declareProperty(formMetadataProp)
        .declareProperty(resourceMetadataProp)
        .declareProperty(agentDefinitionsMetadataProp)
        .declareProperty(tenantIdProp)
        .declareProperty(deploymentKeyProp);
  }

  /**
   * Create an empty DeploymentRecordV1 that can be used as a starting point for reconstructing
   * deployments: The tenantId must be set to "", otherwise its default is "<default>" which breaks
   * /* searching iteratively in RocksDB
   */
  public static DeploymentRecordV1 emptyCommandForReconstruction() {
    return new DeploymentRecordV1().setTenantId("");
  }

  public ValueArray<ProcessMetadataV1> processesMetadata() {
    return processesMetadataProp;
  }

  public ValueArray<DeploymentResource> resources() {
    return resourcesProp;
  }

  public ValueArray<DecisionRecordV1> decisionsMetadata() {
    return decisionMetadataProp;
  }

  public ValueArray<DecisionRequirementsMetadataRecordV1> decisionRequirementsMetadata() {
    return decisionRequirementsMetadataProp;
  }

  public ValueArray<FormMetadataRecordV1> formMetadata() {
    return formMetadataProp;
  }

  public ValueArray<ResourceMetadataRecordV1> resourceMetadata() {
    return resourceMetadataProp;
  }

  /**
   * @return the mutable list of agent definitions created for this deployment. Internal-only,
   *     populated by {@code AgentDefinitionTransformer}; not part of the public {@link
   *     DeploymentRecordValue} interface.
   */
  public ValueArray<AgentDefinitionRecordV1> agentDefinitionsMetadata() {
    return agentDefinitionsMetadataProp;
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

    for (final ProcessMetadataV1 processRecord : processesMetadataProp) {
      final ProcessMetadataV1 copiedProcessRecord = new ProcessMetadataV1();
      final var copyBuffer = BufferUtil.createCopy(processRecord);
      copiedProcessRecord.wrap(copyBuffer);
      processesMeta.add(copiedProcessRecord);
    }

    return processesMeta;
  }

  @Override
  public List<DecisionRecordValue> getDecisionsMetadata() {
    final var metadataList = new ArrayList<DecisionRecordValue>();

    for (final DecisionRecordV1 metadata : decisionMetadataProp) {
      final var copyRecord = new DecisionRecordV1();
      final var copyBuffer = BufferUtil.createCopy(metadata);
      copyRecord.wrap(copyBuffer);
      metadataList.add(copyRecord);
    }

    return metadataList;
  }

  @Override
  public List<DecisionRequirementsMetadataValue> getDecisionRequirementsMetadata() {
    final var metadataList = new ArrayList<DecisionRequirementsMetadataValue>();

    for (final DecisionRequirementsMetadataRecordV1 metadata : decisionRequirementsMetadataProp) {
      final var copyRecord = new DecisionRequirementsMetadataRecordV1();
      final var copyBuffer = BufferUtil.createCopy(metadata);
      copyRecord.wrap(copyBuffer);
      metadataList.add(copyRecord);
    }

    return metadataList;
  }

  @Override
  public List<FormMetadataValue> getFormMetadata() {
    final var metadataList = new ArrayList<FormMetadataValue>();

    for (final FormMetadataRecordV1 metadata : formMetadataProp) {
      final var copyRecord = new FormMetadataRecordV1();
      final var copyBuffer = BufferUtil.createCopy(metadata);
      copyRecord.wrap(copyBuffer);
      metadataList.add(copyRecord);
    }

    return metadataList;
  }

  @Override
  public List<ResourceMetadataValue> getResourceMetadata() {
    final var metadataList = new ArrayList<ResourceMetadataValue>();

    for (final ResourceMetadataRecordV1 metadata : resourceMetadataProp) {
      final var copyRecord = new ResourceMetadataRecordV1();
      final var copyBuffer = BufferUtil.createCopy(metadata);
      copyRecord.wrap(copyBuffer);
      metadataList.add(copyRecord);
    }
    return metadataList;
  }

  @Override
  public long getDeploymentKey() {
    return deploymentKeyProp.getValue();
  }

  public DeploymentRecordV1 setDeploymentKey(final long deploymentKey) {
    deploymentKeyProp.setValue(deploymentKey);
    return this;
  }

  /**
   * @return the last key whose Deployment was reconstructed
   */
  @JsonIgnore
  public long getReconstructionKey() {
    return reconstructionKeyProp.getValue();
  }

  public DeploymentRecordV1 setReconstructionKey(final long deploymentKey) {
    reconstructionKeyProp.setValue(deploymentKey);
    return this;
  }

  public void resetResources() {
    resourcesProp.reset();
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public DeploymentRecordV1 setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  /**
   * Returns {@code true} if every metadata entry in this deployment record has its {@code
   * duplicate} flag set, {@code false} if at least one entry is marked as new or changed. The
   * duplicate flag is set during metadata creation by the individual resource transformers.
   */
  public boolean hasDuplicatesOnly() {
    return processesMetadata().stream().allMatch(ProcessMetadataV1::isDuplicate)
        && decisionRequirementsMetadata().stream()
            .allMatch(DecisionRequirementsMetadataValue::isDuplicate)
        && formMetadata().stream().allMatch(FormMetadataValue::isDuplicate)
        && resourceMetadata().stream().allMatch(ResourceMetadataValue::isDuplicate);
  }

  /**
   * Returns the resource name (filename) of the DRG that contains the given decision. Decisions
   * don't carry their own resource name — they belong to a decision requirements graph (DRG), and
   * the DRG carries the resource name.
   *
   * @param decision the decision metadata to look up
   * @return the resource name of the parent DRG, or {@code null} if not found
   */
  @JsonIgnore
  public String getResourceNameForDecision(final DecisionRecordValue decision) {
    for (final var drg : decisionRequirementsMetadataProp) {
      if (drg.getDecisionRequirementsKey() == decision.getDecisionRequirementsKey()) {
        return drg.getResourceName();
      }
    }
    return null;
  }
}
