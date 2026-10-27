/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import java.util.Map;
import org.agrona.DirectBuffer;

public interface ExecutableFlowElement {

  DirectBuffer getId();

  DirectBuffer getName();

  DirectBuffer getDocumentation();

  BpmnElementType getElementType();

  ExecutableFlowElement getFlowScope();

  BpmnEventType getEventType();

  Map<String, String> getProperties();
}
