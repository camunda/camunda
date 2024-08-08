/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator;

public enum BpmnFeature {

  // default
  NONE,

  // tasks
  SERVICE_TASK,
  USER_TASK,
  UNDEFINED_TASK,

  // gateways
  EXCLUSIVE_GATEWAY,
  PARALLEL_GATEWAY,

  // events
  BOUNDARY_EVENT,
  INTERMEDIATE_CATCH_EVENT,
  INTERMEDIATE_THROW_EVENT,
  END_EVENT,

  // event types
  MESSAGE_EVENT,
  SIGNAL_EVENT,
  COMPENSATION_EVENT,
  TERMINATE_EVENT,

  // activities
  EMBEDDED_SUBPROCESS,

  // patterns
  MULTIPLE_OUTGOING_SEQUENCE_FLOWS,
}
