/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.ConsentState;
import io.camunda.zeebe.protocol.impl.record.value.consent.ConsentRecord;

public interface MutableConsentState extends ConsentState {

  void putGrant(ConsentRecord grant);

  void removeGrant(long consentKey);

  void putApproval(ConsentRecord approval);

  void addParkedJob(String subject, String connection, String operation, long jobKey);

  /** Removes the job from the parked index, whatever it waited on. Does nothing if not parked. */
  void removeParkedJob(long jobKey);
}
