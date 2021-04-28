/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.mutable;

import io.zeebe.engine.state.immutable.ElementInstanceState;
import io.zeebe.engine.state.instance.AwaitProcessInstanceResultMetadata;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

public interface MutableElementInstanceState extends ElementInstanceState {

  ElementInstance newInstance(long key, ProcessInstanceRecord value, ProcessInstanceIntent state);

  ElementInstance newInstance(
      ElementInstance parent, long key, ProcessInstanceRecord value, ProcessInstanceIntent state);

  void removeInstance(long key);

  void updateInstance(ElementInstance scopeInstance);

  void updateInstance(long key, Consumer<ElementInstance> modifier);

  void setAwaitResultRequestMetadata(
      long processInstanceKey, AwaitProcessInstanceResultMetadata metadata);

  /**
   * Increments the number that counts how often the given sequence flow has been taken.
   *
   * <p>The number helps to determine if a parallel gateway can be activated or not. It should be
   * incremented when one of the incoming sequence flows is taken.
   *
   * @param flowScopeKey the key of the flow scope that contains the gateway
   * @param gatewayElementId the element id of the gateway that is the target of the sequence flow
   * @param sequenceFlowElementId the element id of the sequence flow that is taken
   */
  void incrementNumberOfTakenSequenceFlows(
      final long flowScopeKey,
      final DirectBuffer gatewayElementId,
      final DirectBuffer sequenceFlowElementId);

  /**
   * Decrements the numbers that counts how often a sequence flow of the given gateway has been
   * taken.
   *
   * <p>The number helps to determine if a parallel gateway can be activated or not. It should be
   * decremented when the gateway is activated.
   *
   * @param flowScopeKey the key of the flow scope that contains the gateway
   * @param gatewayElementId the element id of the gateway
   */
  void decrementNumberOfTakenSequenceFlows(
      final long flowScopeKey, final DirectBuffer gatewayElementId);
}
