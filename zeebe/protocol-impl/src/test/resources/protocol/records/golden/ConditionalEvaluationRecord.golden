/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.conditional;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariablesPropertyGroup;
import io.camunda.zeebe.protocol.record.value.ConditionalEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.ConditionalStartedProcessInstanceValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.VariableMetadataValue;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.agrona.DirectBuffer;

public final class ConditionalEvaluationRecord extends UnifiedRecordValue
    implements ConditionalEvaluationRecordValue {

  // Static StringValue keys for property names
  public static final StringValue PROCESS_DEFINITION_KEY_KEY =
      new StringValue("processDefinitionKey");
  public static final StringValue TENANT_ID_KEY = new StringValue("tenantId");
  public static final StringValue STARTED_PROCESS_INSTANCES_KEY =
      new StringValue("startedProcessInstances");

  private final LongProperty processDefinitionKeyProp =
      new LongProperty(PROCESS_DEFINITION_KEY_KEY, -1L);
  private final VariablesPropertyGroup variablesPropertyGroup = new VariablesPropertyGroup();
  private final StringProperty tenantIdProp =
      new StringProperty(TENANT_ID_KEY, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final ArrayProperty<ConditionalStartedProcessInstance> startedProcessInstancesProp =
      new ArrayProperty<>(STARTED_PROCESS_INSTANCES_KEY, ConditionalStartedProcessInstance::new);

  public ConditionalEvaluationRecord() {
    super(3);
    declareProperty(processDefinitionKeyProp)
        .declareProperty(tenantIdProp)
        .declareProperty(startedProcessInstancesProp);
    declareProperties(variablesPropertyGroup);
  }

  public void wrap(final ConditionalEvaluationRecord other) {
    processDefinitionKeyProp.setValue(other.getProcessDefinitionKey());
    variablesPropertyGroup.copyFrom(other.variablesPropertyGroup);
    tenantIdProp.setValue(other.getTenantId());

    startedProcessInstancesProp.reset();
    for (final ConditionalStartedProcessInstance instance : other.startedProcessInstances()) {
      startedProcessInstancesProp.add().copy(instance);
    }
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public ConditionalEvaluationRecord setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProp.setValue(processDefinitionKey);
    return this;
  }

  @Override
  public List<ConditionalStartedProcessInstanceValue> getStartedProcessInstances() {
    return StreamSupport.stream(startedProcessInstancesProp.spliterator(), false)
        .map(ConditionalStartedProcessInstanceValue.class::cast)
        .toList();
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variablesPropertyGroup.getVariablesBuffer();
  }

  @Override
  public Map<String, VariableMetadataValue> getVariablesMetadata() {
    return variablesPropertyGroup.getMetadata();
  }

  public ConditionalEvaluationRecord setVariablesMetadata(
      final Map<String, ? extends VariableMetadataValue> variablesMetadata) {
    variablesPropertyGroup.setMetadata(variablesMetadata);
    return this;
  }

  @Override
  public Map<String, Object> getVariables() {
    return variablesPropertyGroup.getVariables();
  }

  public ConditionalEvaluationRecord setVariables(final DirectBuffer variables) {
    variablesPropertyGroup.setVariables(variables);
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public ConditionalEvaluationRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  /**
   * Returns the internal ArrayProperty for direct manipulation by the processor. This is used to
   * add started instances during processing.
   *
   * @return the array property of started process instances
   */
  @JsonIgnore
  public ArrayProperty<ConditionalStartedProcessInstance> startedProcessInstances() {
    return startedProcessInstancesProp;
  }

  /**
   * Adds a started process instance to the record.
   *
   * @param processDefinitionKey the process definition key
   * @param processInstanceKey the process instance key
   * @return this record for fluent chaining
   */
  public ConditionalEvaluationRecord addStartedProcessInstance(
      final long processDefinitionKey, final long processInstanceKey) {
    startedProcessInstancesProp
        .add()
        .setProcessDefinitionKey(processDefinitionKey)
        .setProcessInstanceKey(processInstanceKey);
    return this;
  }
}
