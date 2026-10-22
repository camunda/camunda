/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.swim;

import io.camunda.zeebe.broker.system.syspartition.detector.MemberLivenessState;

/**
 * What the reports of all observers add up to for one broker.
 *
 * @param memberId the broker
 * @param state its consolidated liveness; dead once confirmed over the detection window
 * @param goneForMillis how long every confirming observer has continuously not seen it, i.e. the
 *     shortest of their times; 0 unless some observer confirms it is out of sight
 * @param confirmations how many credible observers do not see it
 * @param visibleTo how many observers see it, itself included when it reports
 */
record BrokerView(
    String memberId,
    MemberLivenessState state,
    long goneForMillis,
    int confirmations,
    int visibleTo) {

  static BrokerView unknown(final String memberId) {
    return new BrokerView(memberId, MemberLivenessState.UNKNOWN, 0, 0, 0);
  }
}
