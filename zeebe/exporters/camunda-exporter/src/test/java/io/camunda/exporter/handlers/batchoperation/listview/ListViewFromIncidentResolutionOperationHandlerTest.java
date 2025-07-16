/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation.listview;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;

class ListViewFromIncidentResolutionOperationHandlerTest
    extends AbstractProcessInstanceFromOperationItemHandlerTest<IncidentRecordValue> {

  ListViewFromIncidentResolutionOperationHandlerTest() {
    super(new ListViewFromIncidentResolutionOperationHandler(INDEX_NAME, CACHE));
  }

  @Override
  protected io.camunda.zeebe.protocol.record.Record<IncidentRecordValue> createCompletedRecord() {
    return factory.generateRecord(ValueType.INCIDENT, b -> b.withIntent(IncidentIntent.RESOLVED));
  }

  @Override
  protected Record<IncidentRecordValue> createRejectedRecord() {
    return factory.generateRecord(
        ValueType.INCIDENT,
        b -> b.withRejectionType(RejectionType.NOT_FOUND).withIntent(IncidentIntent.RESOLVE));
  }
}
