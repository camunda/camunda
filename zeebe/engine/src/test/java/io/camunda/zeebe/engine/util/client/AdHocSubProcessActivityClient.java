/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessActivityActivationRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.AdHocSubProcessActivityActivationIntent;
import io.camunda.zeebe.protocol.record.value.AdHocSubProcessActivityActivationRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.function.Function;

public class AdHocSubProcessActivityClient {
  private static final Function<Long, Record<AdHocSubProcessActivityActivationRecordValue>>
      SUCCESS_EXPECTATION =
          (position) ->
              RecordingExporter.adHocSubProcessActivityActivationRecords()
                  .withIntent(AdHocSubProcessActivityActivationIntent.ACTIVATED)
                  .withSourceRecordPosition(position)
                  .getFirst();
  private static final Function<Long, Record<AdHocSubProcessActivityActivationRecordValue>>
      REJECTION_EXPECTATION =
          (position) ->
              RecordingExporter.adHocSubProcessActivityActivationRecords()
                  .onlyCommandRejections()
                  .withIntent(AdHocSubProcessActivityActivationIntent.ACTIVATE)
                  .withSourceRecordPosition(position)
                  .getFirst();

  private final CommandWriter writer;
  private final AdHocSubProcessActivityActivationRecord adHocSubProcessActivityActivationRecord =
      new AdHocSubProcessActivityActivationRecord();
  private Function<Long, Record<AdHocSubProcessActivityActivationRecordValue>> expectation =
      SUCCESS_EXPECTATION;
  private List<String> authorizedTenantIds = List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public AdHocSubProcessActivityClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public Record<AdHocSubProcessActivityActivationRecordValue> activate() {
    final var position =
        writer.writeCommand(
            AdHocSubProcessActivityActivationIntent.ACTIVATE,
            adHocSubProcessActivityActivationRecord,
            authorizedTenantIds.toArray(new String[0]));

    return expectation.apply(position);
  }

  public AdHocSubProcessActivityClient expectRejection() {
    expectation = REJECTION_EXPECTATION;
    return this;
  }

  public AdHocSubProcessActivityClient withAdHocSubProcessInstanceKey(
      final String adHocSubProcessInstanceKey) {
    adHocSubProcessActivityActivationRecord.setAdHocSubProcessInstanceKey(
        adHocSubProcessInstanceKey);
    return this;
  }

  public AdHocSubProcessActivityClient withElementIds(final String... elementIds) {
    for (final String elementId : elementIds) {
      adHocSubProcessActivityActivationRecord.elements().add().setElementId(elementId);
    }
    return this;
  }

  public AdHocSubProcessActivityClient withAuthorizedTenantIds(final String... tenantIds) {
    authorizedTenantIds = List.of(tenantIds);
    return this;
  }
}
