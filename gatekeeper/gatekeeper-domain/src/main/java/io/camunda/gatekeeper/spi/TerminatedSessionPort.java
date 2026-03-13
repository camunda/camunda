/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spi;

import io.camunda.gatekeeper.model.session.TerminatedSession;
import java.time.Instant;

/** Outbound port for persisting terminated (revoked) sessions. */
public interface TerminatedSessionPort {

  void save(TerminatedSession terminatedSession);

  boolean exists(String sessionId);

  void deleteOlderThan(Instant cutoff);
}
