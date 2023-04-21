/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import java.util.List;

public class ProcessInstanceMigrationRecord extends UnifiedRecordValue {

  final long processInstanceKey;
  final MigrationPlan migrationPlan;

  public ProcessInstanceMigrationRecord(
      final long processInstanceKey, final MigrationPlan migrationPlan) {
    this.processInstanceKey = processInstanceKey;
    this.migrationPlan = migrationPlan;
  }

  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public MigrationPlan getMigrationPlan() {
    return migrationPlan;
  }

  public record MigrationPlan(
      long targetProcessDefinitionKey, List<MappingInstruction> mappingInstructions) {}

  public record MappingInstruction(String sourceElementId, String targetElementId) {}
}
