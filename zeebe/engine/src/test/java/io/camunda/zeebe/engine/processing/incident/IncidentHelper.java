/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.incident;

import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValueAssert;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;

public class IncidentHelper {

  public static IncidentRecordValueAssert assertIncidentCreated(
      final Record<IncidentRecordValue> incident,
      final Record<ProcessInstanceRecordValue> elementInstance) {
    return assertIncidentCreated(incident, elementInstance, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  public static IncidentRecordValueAssert assertIncidentCreated(
      final Record<IncidentRecordValue> incident,
      final Record<ProcessInstanceRecordValue> elementInstance,
      final String tenantId) {
    return Assertions.assertThat(incident.getValue())
        .hasElementInstanceKey(elementInstance.getKey())
        .hasElementId(elementInstance.getValue().getElementId())
        .hasTenantId(tenantId);
  }
}
