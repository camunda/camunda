/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import java.util.concurrent.atomic.LongAdder;

/** Counts what the store and its cursors do; nothing reads them in production yet. */
final class WriteBehindMetrics {

  final LongAdder iteratorsOpened = new LongAdder();
  final LongAdder keyIndexLoads = new LongAdder();
  final LongAdder keyIndexDiscardedLoads = new LongAdder();
  final LongAdder keyIndexDisables = new LongAdder();
  final LongAdder indexedScans = new LongAdder();
  final LongAdder keyIndexSwitches = new LongAdder();
}
