/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import java.util.UUID;

/**
 * Defines lease-token generation in one place. Per zeebe/docs/adr/0005-810-job-lease.md (D1), the
 * token's opacity is deliberate so that the engine stays free to change how tokens are produced;
 * keeping generation to one implementation means such a change only has one place to update.
 *
 * <p>Per the same ADR (D2), the token must be generated exactly once per job, at command-processing
 * time, and written into the {@code ACTIVATED} event; replay restores it from that event rather
 * than regenerating it. Call this only at command-processing time — never from an event applier or
 * any other replay path, as a second generation site would break replay determinism.
 */
public final class LeaseTokens {

  private LeaseTokens() {}

  /**
   * Returns an opaque token; callers must not parse it. Call once per activated job — never reuse
   * one token across the jobs of a batch. Random with enough entropy that collisions between leased
   * jobs are negligible.
   */
  public static String generate() {
    return UUID.randomUUID().toString();
  }
}
