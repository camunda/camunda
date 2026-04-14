/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.replication;

/**
 * Pairs a primary database LSN with the Zeebe record position at the time of flush. Both values are
 * monotonically increasing, so a queue of entries preserves natural ordering.
 */
public record LsnPositionEntry(long lsn, long position) {}
