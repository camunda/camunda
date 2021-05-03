/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.immutable;

import io.zeebe.engine.state.instance.AwaitProcessInstanceResultMetadata;
import io.zeebe.engine.state.instance.ElementInstance;
import java.util.List;
import org.agrona.DirectBuffer;

public interface ElementInstanceState {

  ElementInstance getInstance(long key);

  List<ElementInstance> getChildren(long parentKey);

  AwaitProcessInstanceResultMetadata getAwaitResultRequestMetadata(long processInstanceKey);

  /**
   * Returns the number of the taken sequence flows that are connected to the given parallel
   * (joining) gateway. Each sequence flow counts only as one, even if it is taken multiple times.
   *
   * <p>The number helps to determine if a parallel gateway can be activated or not.
   *
   * @param flowScopeKey the key of the flow scope that contains the gateway
   * @param gatewayElementId the element id of the gateway
   * @return the number of taken sequence flows of the given gateway
   */
  int getNumberOfTakenSequenceFlows(final long flowScopeKey, final DirectBuffer gatewayElementId);
}
