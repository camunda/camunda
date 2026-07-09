/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

/**
 * Creates one {@link Task} per assigned source partition. The {@code epoch} is the runtime's {@link
 * OwnershipEpoch} — the coordinator fencing token of the membership that owns the partition; a task
 * that writes to an external store stamps it (with its commit offset) onto every write batch so the
 * store can fence a zombie's stale data. Tasks that produce no external writes may ignore it (the
 * {@code IntFunction} builder overload does exactly that).
 *
 * @param <R> the record type the task processes
 */
@FunctionalInterface
public interface TaskFactory<R> {

  Task<R> create(int partition, OwnershipEpoch epoch);
}
