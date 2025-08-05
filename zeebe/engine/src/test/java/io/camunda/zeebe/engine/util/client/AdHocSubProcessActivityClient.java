/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessInstructionRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.AdHocSubProcessInstructionIntent;
import io.camunda.zeebe.protocol.record.value.AdHocSubProcessInstructionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.function.Function;

public class AdHocSubProcessActivityClient {
  private static final Function<Long, Record<AdHocSubProcessInstructionRecordValue>>
      SUCCESS_EXPECTATION =
          (position) ->
              RecordingExporter.adHocSubProcessInstructionRecords()
                  .withIntent(AdHocSubProcessInstructionIntent.ACTIVATED)
                  .withSourceRecordPosition(position)
                  .getFirst();
  private static final Function<Long, Record<AdHocSubProcessInstructionRecordValue>>
      REJECTION_EXPECTATION =
          (position) ->
              RecordingExporter.adHocSubProcessInstructionRecords()
                  .onlyCommandRejections()
                  .withIntent(AdHocSubProcessInstructionIntent.ACTIVATE)
                  .withSourceRecordPosition(position)
                  .getFirst();

  private final CommandWriter writer;
  private final AdHocSubProcessInstructionRecord adHocSubProcessInstructionRecord =
      new AdHocSubProcessInstructionRecord();
  private Function<Long, Record<AdHocSubProcessInstructionRecordValue>> expectation =
      SUCCESS_EXPECTATION;
  private List<String> authorizedTenantIds = List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public AdHocSubProcessActivityClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public Record<AdHocSubProcessInstructionRecordValue> activate() {
    final var position =
        writer.writeCommand(
            AdHocSubProcessInstructionIntent.ACTIVATE,
            adHocSubProcessInstructionRecord,
            authorizedTenantIds.toArray(new String[0]));

    return expectation.apply(position);
  }

  public AdHocSubProcessActivityClient expectRejection() {
    expectation = REJECTION_EXPECTATION;
    return this;
  }

  public AdHocSubProcessActivityClient withAdHocSubProcessInstanceKey(
      final long adHocSubProcessInstanceKey) {
    adHocSubProcessInstructionRecord.setAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey);
    return this;
  }

  public AdHocSubProcessActivityClient withElementIds(final String... elementIds) {
    for (final String elementId : elementIds) {
      adHocSubProcessInstructionRecord.activateElements().add().setElementId(elementId);
    }
    return this;
  }

  public AdHocSubProcessActivityClient withAuthorizedTenantIds(final String... tenantIds) {
    authorizedTenantIds = List.of(tenantIds);
    return this;
  }
}
