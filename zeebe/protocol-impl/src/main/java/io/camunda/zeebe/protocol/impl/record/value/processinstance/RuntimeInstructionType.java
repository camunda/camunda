/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

/** Defines the types of runtime instructions that can be used in process instance creation. */
public enum RuntimeInstructionType {
  /** Instruction to cancel a process instance. */
  SUSPEND_PROCESS_INSTANCE
}
