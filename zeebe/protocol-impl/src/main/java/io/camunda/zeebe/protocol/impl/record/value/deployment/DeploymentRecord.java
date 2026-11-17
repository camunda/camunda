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
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackIgnore;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.agentdefinition.AgentDefinitionRecord;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsMetadataValue;
import io.camunda.zeebe.protocol.record.value.deployment.FormMetadataValue;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.protocol.record.value.deployment.ResourceMetadataValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class DeploymentRecord extends MsgpackBufferValue<DeploymentRecord>
    implements MsgpackRecordValue, DeploymentRecordValue {

  public static final String RESOURCES = "resources";
  public static final String PROCESSES = "processesMetadata";

  final List<DeploymentResource> resources = new ArrayList<>();
  final List<ProcessMetadata> processesMetadata = new ArrayList<>();
  final List<DecisionRequirementsMetadataRecord> decisionRequirementsMetadata = new ArrayList<>();
  final List<DecisionRecord> decisionsMetadata = new ArrayList<>();
  final List<FormMetadataRecord> formMetadata = new ArrayList<>();
  final List<ResourceMetadataRecord> resourceMetadata = new ArrayList<>();

  /**
   * Agent definitions created at deploy time for elements carrying a recognized agent marker (see
   * {@code AgentElementTypeTransformer}). Internal-only: unlike the other metadata arrays, this is
   * not exposed on the public {@link DeploymentRecordValue} interface, since agent definitions are
   * a deploy-time engine concern rather than a deployment result reported to clients.
   */
  final List<AgentDefinitionRecord> agentDefinitionsMetadata = new ArrayList<>();

  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long deploymentKey = -1L;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  /**
   * Never stored: kept as a separate property only because the golden file of the record lists it.
   */
  @MsgpackIgnore
  private final LongProperty reconstructionKeyProp = new LongProperty("reconstructionKey", -1);

  /**
   * Create an empty DeploymentRecord that can be used as a starting point for reconstructing
   * deployments: The tenantId must be set to "", otherwise its default is "<default>" which breaks
   * /* searching iteratively in RocksDB
   */
  public static DeploymentRecord emptyCommandForReconstruction() {
    return new DeploymentRecord().setTenantId("");
  }

  public ValueArray<ProcessMetadata> processesMetadata() {
    return new ListValueArray<>(processesMetadata, ProcessMetadata::new);
  }

  public ValueArray<DeploymentResource> resources() {
    return new ListValueArray<>(resources, DeploymentResource::new);
  }

  public ValueArray<DecisionRecord> decisionsMetadata() {
    return new ListValueArray<>(decisionsMetadata, DecisionRecord::new);
  }

  public ValueArray<DecisionRequirementsMetadataRecord> decisionRequirementsMetadata() {
    return new ListValueArray<>(
        decisionRequirementsMetadata, DecisionRequirementsMetadataRecord::new);
  }

  public ValueArray<FormMetadataRecord> formMetadata() {
    return new ListValueArray<>(formMetadata, FormMetadataRecord::new);
  }

  public ValueArray<ResourceMetadataRecord> resourceMetadata() {
    return new ListValueArray<>(resourceMetadata, ResourceMetadataRecord::new);
  }

  /**
   * @return the mutable list of agent definitions created for this deployment. Internal-only,
   *     populated by {@code AgentDefinitionTransformer}; not part of the public {@link
   *     DeploymentRecordValue} interface.
   */
  public ValueArray<AgentDefinitionRecord> agentDefinitionsMetadata() {
    return new ListValueArray<>(agentDefinitionsMetadata, AgentDefinitionRecord::new);
  }

  @Override
  public List<io.camunda.zeebe.protocol.record.value.deployment.DeploymentResource> getResources() {
    final List<io.camunda.zeebe.protocol.record.value.deployment.DeploymentResource> copies =
        new ArrayList<>();

    for (final DeploymentResource resource : resources) {
      final DeploymentResource copiedResource = new DeploymentResource();
      final var copyBuffer = BufferUtil.createCopy(resource);
      copiedResource.wrap(copyBuffer);
      copies.add(copiedResource);
    }

    return copies;
  }

  @Override
  public List<ProcessMetadataValue> getProcessesMetadata() {
    final List<ProcessMetadataValue> processesMeta = new ArrayList<>();

    for (final ProcessMetadata processRecord : processesMetadata) {
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

    for (final DecisionRecord metadata : decisionsMetadata) {
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

    for (final DecisionRequirementsMetadataRecord metadata : decisionRequirementsMetadata) {
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

    for (final FormMetadataRecord metadata : formMetadata) {
      final var copyRecord = new FormMetadataRecord();
      final var copyBuffer = BufferUtil.createCopy(metadata);
      copyRecord.wrap(copyBuffer);
      metadataList.add(copyRecord);
    }

    return metadataList;
  }

  @Override
  public List<ResourceMetadataValue> getResourceMetadata() {
    final var metadataList = new ArrayList<ResourceMetadataValue>();

    for (final ResourceMetadataRecord metadata : resourceMetadata) {
      final var copyRecord = new ResourceMetadataRecord();
      final var copyBuffer = BufferUtil.createCopy(metadata);
      copyRecord.wrap(copyBuffer);
      metadataList.add(copyRecord);
    }
    return metadataList;
  }

  @Override
  public long getDeploymentKey() {
    return deploymentKey;
  }

  public DeploymentRecord setDeploymentKey(final long deploymentKey) {
    this.deploymentKey = deploymentKey;
    return this;
  }

  /**
   * @return the last key whose Deployment was reconstructed
   */
  @JsonIgnore
  public long getReconstructionKey() {
    return reconstructionKeyProp.getValue();
  }

  public DeploymentRecord setReconstructionKey(final long deploymentKey) {
    reconstructionKeyProp.setValue(deploymentKey);
    return this;
  }

  public void resetResources() {
    resources.clear();
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public DeploymentRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  /**
   * Returns {@code true} if every metadata entry in this deployment record has its {@code
   * duplicate} flag set, {@code false} if at least one entry is marked as new or changed. The
   * duplicate flag is set during metadata creation by the individual resource transformers.
   */
  public boolean hasDuplicatesOnly() {
    return processesMetadata().stream().allMatch(ProcessMetadata::isDuplicate)
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
  @SuppressWarnings("NullAway")
  public String getResourceNameForDecision(final DecisionRecordValue decision) {
    for (final var drg : decisionRequirementsMetadata) {
      if (drg.getDecisionRequirementsKey() == decision.getDecisionRequirementsKey()) {
        return drg.getResourceName();
      }
    }
    return null;
  }

  @Override
  protected MsgpackCodec<DeploymentRecord> codec() {
    return DeploymentRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<DeploymentRecord> ops() {
    return DeploymentRecordOps.INSTANCE;
  }

  /**
   * The mutable accessor of one of the lists: it adds and removes the elements of the list, so that
   * code written against the former {@code ArrayProperty} keeps working.
   */
  private static final class ListValueArray<T> implements ValueArray<T> {

    final List<T> items;
    final Supplier<T> factory;

    private ListValueArray(final List<T> items, final Supplier<T> factory) {
      this.items = items;
      this.factory = factory;
    }

    @Override
    public T add() {
      final var item = factory.get();
      items.add(item);
      return item;
    }

    @Override
    public T add(final int index) {
      final var item = factory.get();
      items.add(index, item);
      return item;
    }

    @Override
    public T get(final int index) {
      return items.get(index);
    }

    @Override
    public T remove(final int index) {
      return items.remove(index);
    }

    @Override
    public int size() {
      return items.size();
    }

    @Override
    public Stream<T> stream() {
      return items.stream();
    }

    @Override
    public Iterator<T> iterator() {
      return items.iterator();
    }
  }
}
