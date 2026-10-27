/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

/**
 * Marker interface for exporter-driven RDBMS writers.
 *
 * <p>Implementations must be registered in {@link io.camunda.db.rdbms.write.RdbmsWriters} and route
 * all write operations through {@link io.camunda.db.rdbms.write.queue.ExecutionQueue} for batching
 * and transaction management. Writers that bypass {@code ExecutionQueue} (e.g. synchronous,
 * request-scoped session management) must not implement this interface.
 */
public interface RdbmsWriter {}
