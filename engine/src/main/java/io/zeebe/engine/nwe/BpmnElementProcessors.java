/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.nwe;

import io.zeebe.engine.nwe.behavior.BpmnBehaviors;
import io.zeebe.engine.nwe.container.MultiInstanceBodyProcessor;
import io.zeebe.engine.nwe.container.ProcessProcessor;
import io.zeebe.engine.nwe.container.SubProcessProcessor;
import io.zeebe.engine.nwe.gateway.ExclusiveGatewayProcessor;
import io.zeebe.engine.nwe.task.ServiceTaskProcessor;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElement;
import io.zeebe.protocol.record.value.BpmnElementType;
import java.util.EnumMap;
import java.util.Map;

public final class BpmnElementProcessors {

  private final Map<BpmnElementType, BpmnElementProcessor<?>> processors =
      new EnumMap<>(BpmnElementType.class);

  public BpmnElementProcessors(final BpmnBehaviors bpmnBehaviors) {
    // tasks
    processors.put(BpmnElementType.SERVICE_TASK, new ServiceTaskProcessor(bpmnBehaviors));
    // gateways
    processors.put(BpmnElementType.EXCLUSIVE_GATEWAY, new ExclusiveGatewayProcessor(bpmnBehaviors));
    // containers
    processors.put(BpmnElementType.PROCESS, new ProcessProcessor(bpmnBehaviors));
    processors.put(BpmnElementType.SUB_PROCESS, new SubProcessProcessor(bpmnBehaviors));
    processors.put(
        BpmnElementType.MULTI_INSTANCE_BODY, new MultiInstanceBodyProcessor(bpmnBehaviors));
  }

  public <T extends ExecutableFlowElement> BpmnElementProcessor<T> getProcessor(
      final BpmnElementType bpmnElementType) {
    if (bpmnElementType == BpmnElementType.SUB_PROCESS
        || bpmnElementType == BpmnElementType.PROCESS) {
      return null;
    }

    final var processor = (BpmnElementProcessor<T>) processors.get(bpmnElementType);
    if (processor == null) {
      //      throw new UnsupportedOperationException(
      //          String.format("no processor found for BPMN element type '%s'", bpmnElementType));
    }
    return processor;
  }

  public <T extends ExecutableFlowElement> BpmnElementContainerProcessor<T> getContainerProcessor(
      final BpmnElementType bpmnElementType) {
    switch (bpmnElementType) {
      case PROCESS:
      case SUB_PROCESS:
      case MULTI_INSTANCE_BODY:
        return (BpmnElementContainerProcessor<T>) processors.get(bpmnElementType);
      default:
        throw new UnsupportedOperationException(
            String.format(
                "no container processor found for BPMN element type '%s'", bpmnElementType));
    }
  }
}
