/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.hub;

import io.camunda.zeebe.protocol.record.value.PolicyStatusRecordValue;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateStatus;
import org.jspecify.annotations.NullMarked;

/**
 * The policy status of partition 1, as the engine answers a status query.
 *
 * @param committedSeqNo the Hub sequence of the committed policy, or 0 if none was committed
 * @param updateStatus the status of the open update
 * @param openUpdateId the id of the open update, or empty if no update is open
 */
@NullMarked
public record PolicyStatus(
    long committedSeqNo, PolicyUpdateStatus updateStatus, String openUpdateId) {

  static PolicyStatus of(final PolicyStatusRecordValue answer) {
    return new PolicyStatus(answer.getSeqNo(), answer.getUpdateStatus(), answer.getOpenUpdateId());
  }

  /** Whether an update is open, so the Hub receiver must not send the next one. */
  public boolean hasOpenUpdate() {
    return updateStatus != PolicyUpdateStatus.NONE;
  }
}
