/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.protocol.impl.record.value.consent.ConsentRecord;
import java.util.Optional;
import java.util.function.LongPredicate;

/**
 * Consent in engine state: the live grants, the one-time approvals of process instances, and the
 * jobs parked until their subject consents. The gate reads it synchronously, with no read model.
 */
public interface ConsentState {

  /** The live grant of the subject for the operation of the connection, if any. */
  Optional<ConsentRecord> getGrant(String subject, String connection, String operation);

  /** The live grant with this key, if any. */
  Optional<ConsentRecord> getGrantByKey(long consentKey);

  /** The approval the subject gave for the operation within the process instance, if any. */
  Optional<ConsentRecord> getApproval(
      long processInstanceKey, String subject, String connection, String operation);

  /**
   * Visits the keys of the jobs parked on the subject, connection and operation, in key order,
   * starting after {@code afterJobKey}, while the visitor returns {@code true}.
   */
  void visitParkedJobs(
      String subject, String connection, String operation, long afterJobKey, LongPredicate visitor);

  boolean isParked(long jobKey);

  /** Identifies a grant, and the jobs that wait on one, by subject, connection and operation. */
  static String grantId(final String subject, final String connection, final String operation) {
    return subject + '\u001f' + connection + '\u001f' + operation;
  }
}
