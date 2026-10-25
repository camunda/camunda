/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.consent.ConsentRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ConsentIntent;
import io.camunda.zeebe.protocol.record.value.ConsentDecision;
import io.camunda.zeebe.test.util.record.RecordingExporter;

/** Writes consent commands as a given user, and returns what the engine answered. */
public final class ConsentClient {

  private final CommandWriter writer;

  public ConsentClient(final CommandWriter writer) {
    this.writer = writer;
  }

  /** Answers the consent request of a parked job, as the given user. */
  public Record<RecordValue> resolve(
      final long jobKey, final ConsentDecision decision, final String username) {
    final var record = new ConsentRecord().setJobKey(jobKey).setDecision(decision);
    return answerTo(writer.writeCommand(jobKey, ConsentIntent.RESOLVE, username, record));
  }

  /** Grants consent for the operation of the connection, as the given user. */
  public Record<RecordValue> grant(
      final String connection, final String operation, final String username) {
    final var record =
        new ConsentRecord().setSubject(username).setConnection(connection).setOperation(operation);
    return answerTo(writer.writeCommand(ConsentIntent.GRANT, username, record));
  }

  /** Revokes the grant with the key, as the given user. */
  public Record<RecordValue> revoke(
      final long consentKey, final String reason, final String username) {
    final var record = new ConsentRecord().setConsentKey(consentKey).setReason(reason);
    return answerTo(writer.writeCommand(consentKey, ConsentIntent.REVOKE, username, record));
  }

  /** The first follow-up of the command: the event that answers it, or its rejection. */
  private static Record<RecordValue> answerTo(final long position) {
    return RecordingExporter.records()
        .withValueType(ValueType.CONSENT)
        .withSourceRecordPosition(position)
        .filter(r -> r.getRecordType() != io.camunda.zeebe.protocol.record.RecordType.COMMAND)
        .getFirst();
  }
}
