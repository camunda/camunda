/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.generator;

import io.camunda.zeebe.protocol.record.value.BpmnElementType;

/**
 * Groups the identity fields of one BPMN element instance.
 *
 * <p>Passed to {@link ZeebeRecordFactory#processInstanceOp} to avoid a 7-argument method signature.
 *
 * @param key the element-instance key (the record key in Zeebe)
 * @param elementId the BPMN element ID (e.g. {@code "Task_1"})
 * @param type the BPMN element type
 * @param flowScopeKey {@code -1} for the root {@code PROCESS} element; the instance key for
 *     top-level nodes; the enclosing sub-process element-instance key for embedded nodes
 */
record ElementRecord(long key, String elementId, BpmnElementType type, long flowScopeKey) {}
