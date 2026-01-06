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
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;

class ListViewFromProcessInstanceModificationOperationHandlerTest
    extends AbstractProcessInstanceFromOperationItemHandlerTest<
        ProcessInstanceModificationRecordValue> {

  ListViewFromProcessInstanceModificationOperationHandlerTest() {
    super(new ListViewFromProcessInstanceModificationOperationHandler(INDEX_NAME, CACHE));
  }

  @Override
  protected Record<ProcessInstanceModificationRecordValue> createCompletedRecord() {
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE_MODIFICATION,
        b -> b.withIntent(ProcessInstanceModificationIntent.MODIFIED));
  }

  @Override
  protected Record<ProcessInstanceModificationRecordValue> createRejectedRecord() {
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE_MODIFICATION,
        b ->
            b.withRejectionType(RejectionType.NOT_FOUND)
                .withIntent(ProcessInstanceModificationIntent.MODIFY));
  }
}
