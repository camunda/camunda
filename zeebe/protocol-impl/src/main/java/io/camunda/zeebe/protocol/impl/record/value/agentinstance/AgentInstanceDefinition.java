/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agentinstance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryMessageContent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue.AgentHistoryMessageContentValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;

@JsonIgnoreProperties({"encodedLength", "empty"})
public final class AgentInstanceDefinition extends ObjectValue
    implements AgentInstanceRecordValue.AgentInstanceDefinitionValue {

  private final StringProperty modelProp = new StringProperty("model", "");
  private final StringProperty providerProp = new StringProperty("provider", "");
  private final ArrayProperty<AgentHistoryMessageContent> systemPromptProp =
      new ArrayProperty<>("systemPrompt", AgentHistoryMessageContent::new);

  public AgentInstanceDefinition() {
    super(3);
    declareProperty(modelProp).declareProperty(providerProp).declareProperty(systemPromptProp);
  }

  @Override
  public String getModel() {
    return BufferUtil.bufferAsString(modelProp.getValue());
  }

  public AgentInstanceDefinition setModel(final String model) {
    modelProp.setValue(model);
    return this;
  }

  @Override
  public String getProvider() {
    return BufferUtil.bufferAsString(providerProp.getValue());
  }

  public AgentInstanceDefinition setProvider(final String provider) {
    providerProp.setValue(provider);
    return this;
  }

  @Override
  public List<AgentHistoryMessageContentValue> getSystemPrompt() {
    return systemPromptProp.stream()
        .map(
            element -> {
              final var copy = new AgentHistoryMessageContent();
              copy.copy(element);
              return (AgentHistoryMessageContentValue) copy;
            })
        .toList();
  }

  public AgentInstanceDefinition setSystemPrompt(
      final List<? extends AgentHistoryMessageContentValue> systemPrompt) {
    systemPromptProp.reset();
    if (systemPrompt != null) {
      for (final var item : systemPrompt) {
        systemPromptProp.add().copy(item);
      }
    }
    return this;
  }

  public AgentInstanceDefinition addSystemPrompt(final AgentHistoryMessageContent block) {
    systemPromptProp.add().copy(block);
    return this;
  }
}
