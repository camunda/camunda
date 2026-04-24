/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agentinstance;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;

public final class AgentInstanceRecord extends UnifiedRecordValue
    implements AgentInstanceRecordValue {

  private static final StringValue AGENT_INSTANCE_KEY_KEY = new StringValue("agentInstanceKey");
  private static final StringValue ELEMENT_INSTANCE_KEY_KEY = new StringValue("elementInstanceKey");
  private static final StringValue PROCESS_INSTANCE_KEY_KEY = new StringValue("processInstanceKey");
  private static final StringValue PROCESS_DEFINITION_KEY_KEY =
      new StringValue("processDefinitionKey");
  private static final StringValue TENANT_ID_KEY = new StringValue("tenantId");
  private static final StringValue STATUS_KEY = new StringValue("status");
  private static final StringValue MODEL_KEY = new StringValue("model");
  private static final StringValue PROVIDER_KEY = new StringValue("provider");
  private static final StringValue SYSTEM_PROMPT_KEY = new StringValue("systemPrompt");
  private static final StringValue INPUT_TOKENS_KEY = new StringValue("inputTokens");
  private static final StringValue OUTPUT_TOKENS_KEY = new StringValue("outputTokens");
  private static final StringValue MODEL_CALLS_KEY = new StringValue("modelCalls");
  private static final StringValue MAX_TOKENS_KEY = new StringValue("maxTokens");
  private static final StringValue MAX_MODEL_CALLS_KEY = new StringValue("maxModelCalls");
  private static final StringValue MAX_TOOL_CALLS_KEY = new StringValue("maxToolCalls");

  private final LongProperty agentInstanceKeyProp = new LongProperty(AGENT_INSTANCE_KEY_KEY, -1L);
  private final LongProperty elementInstanceKeyProp =
      new LongProperty(ELEMENT_INSTANCE_KEY_KEY, -1L);
  private final LongProperty processInstanceKeyProp =
      new LongProperty(PROCESS_INSTANCE_KEY_KEY, -1L);
  private final LongProperty processDefinitionKeyProp =
      new LongProperty(PROCESS_DEFINITION_KEY_KEY, -1L);
  private final StringProperty tenantIdProp =
      new StringProperty(TENANT_ID_KEY, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final EnumProperty<AgentInstanceStatus> statusProp =
      new EnumProperty<>(STATUS_KEY, AgentInstanceStatus.class, AgentInstanceStatus.IDLE);
  private final StringProperty modelProp = new StringProperty(MODEL_KEY, "");
  private final StringProperty providerProp = new StringProperty(PROVIDER_KEY, "");
  private final StringProperty systemPromptProp = new StringProperty(SYSTEM_PROMPT_KEY, "");
  private final LongProperty inputTokensProp = new LongProperty(INPUT_TOKENS_KEY, 0);
  private final LongProperty outputTokensProp = new LongProperty(OUTPUT_TOKENS_KEY, 0);
  private final LongProperty modelCallsProp = new LongProperty(MODEL_CALLS_KEY, 0);
  private final LongProperty maxTokensProp = new LongProperty(MAX_TOKENS_KEY, -1L);
  private final LongProperty maxModelCallsProp = new LongProperty(MAX_MODEL_CALLS_KEY, -1L);
  private final LongProperty maxToolCallsProp = new LongProperty(MAX_TOOL_CALLS_KEY, -1L);

  public AgentInstanceRecord() {
    super(15);
    declareProperty(agentInstanceKeyProp)
        .declareProperty(elementInstanceKeyProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(processDefinitionKeyProp)
        .declareProperty(tenantIdProp)
        .declareProperty(statusProp)
        .declareProperty(modelProp)
        .declareProperty(providerProp)
        .declareProperty(systemPromptProp)
        .declareProperty(inputTokensProp)
        .declareProperty(outputTokensProp)
        .declareProperty(modelCallsProp)
        .declareProperty(maxTokensProp)
        .declareProperty(maxModelCallsProp)
        .declareProperty(maxToolCallsProp);
  }

  @Override
  public long getAgentInstanceKey() {
    return agentInstanceKeyProp.getValue();
  }

  public AgentInstanceRecord setAgentInstanceKey(final long key) {
    agentInstanceKeyProp.setValue(key);
    return this;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  public AgentInstanceRecord setElementInstanceKey(final long key) {
    elementInstanceKeyProp.setValue(key);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public AgentInstanceRecord setProcessInstanceKey(final long key) {
    processInstanceKeyProp.setValue(key);
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public AgentInstanceRecord setProcessDefinitionKey(final long key) {
    processDefinitionKeyProp.setValue(key);
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public AgentInstanceRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  @Override
  public AgentInstanceStatus getStatus() {
    return statusProp.getValue();
  }

  public AgentInstanceRecord setStatus(final AgentInstanceStatus status) {
    statusProp.setValue(status);
    return this;
  }

  @Override
  public String getModel() {
    return bufferAsString(modelProp.getValue());
  }

  public AgentInstanceRecord setModel(final String model) {
    modelProp.setValue(model);
    return this;
  }

  @Override
  public String getProvider() {
    return bufferAsString(providerProp.getValue());
  }

  public AgentInstanceRecord setProvider(final String provider) {
    providerProp.setValue(provider);
    return this;
  }

  @Override
  public String getSystemPrompt() {
    return bufferAsString(systemPromptProp.getValue());
  }

  public AgentInstanceRecord setSystemPrompt(final String systemPrompt) {
    systemPromptProp.setValue(systemPrompt);
    return this;
  }

  @Override
  public long getInputTokens() {
    return inputTokensProp.getValue();
  }

  public AgentInstanceRecord setInputTokens(final long inputTokens) {
    inputTokensProp.setValue(inputTokens);
    return this;
  }

  @Override
  public long getOutputTokens() {
    return outputTokensProp.getValue();
  }

  public AgentInstanceRecord setOutputTokens(final long outputTokens) {
    outputTokensProp.setValue(outputTokens);
    return this;
  }

  @Override
  public long getModelCalls() {
    return modelCallsProp.getValue();
  }

  public AgentInstanceRecord setModelCalls(final long modelCalls) {
    modelCallsProp.setValue(modelCalls);
    return this;
  }

  @Override
  public long getMaxTokens() {
    return maxTokensProp.getValue();
  }

  public AgentInstanceRecord setMaxTokens(final long maxTokens) {
    maxTokensProp.setValue(maxTokens);
    return this;
  }

  @Override
  public long getMaxModelCalls() {
    return maxModelCallsProp.getValue();
  }

  public AgentInstanceRecord setMaxModelCalls(final long maxModelCalls) {
    maxModelCallsProp.setValue(maxModelCalls);
    return this;
  }

  @Override
  public long getMaxToolCalls() {
    return maxToolCallsProp.getValue();
  }

  public AgentInstanceRecord setMaxToolCalls(final long maxToolCalls) {
    maxToolCallsProp.setValue(maxToolCalls);
    return this;
  }
}
