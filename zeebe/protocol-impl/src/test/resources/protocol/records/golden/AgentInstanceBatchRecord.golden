/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agentinstance;

import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceBatchRecordValue;

public final class AgentInstanceBatchRecord extends UnifiedRecordValue
    implements AgentInstanceBatchRecordValue {

  private final LongProperty processInstanceKeyProperty = new LongProperty("processInstanceKey");
  private final LongProperty processDefinitionKeyProperty =
      new LongProperty("processDefinitionKey", -1L);

  /**
   * The agent instance key to resume iteration from on the next batch cycle. Defaults to {@code -1}
   * to signal that iteration should start from the beginning.
   */
  private final LongProperty agentInstanceKeyProperty = new LongProperty("agentInstanceKey", -1L);

  public AgentInstanceBatchRecord() {
    super(3);
    declareProperty(processInstanceKeyProperty)
        .declareProperty(processDefinitionKeyProperty)
        .declareProperty(agentInstanceKeyProperty);
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProperty.getValue();
  }

  public AgentInstanceBatchRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProperty.setValue(processInstanceKey);
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProperty.getValue();
  }

  public AgentInstanceBatchRecord setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProperty.setValue(processDefinitionKey);
    return this;
  }

  @Override
  public long getAgentInstanceKey() {
    return agentInstanceKeyProperty.getValue();
  }

  public AgentInstanceBatchRecord setAgentInstanceKey(final long agentInstanceKey) {
    agentInstanceKeyProperty.setValue(agentInstanceKey);
    return this;
  }
}
