/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db;

/**
 * Marker interface for generic/untyped {@link DbValue} implementations used to inspect arbitrary
 * stored values, e.g. test helpers that collect state across all column families.
 *
 * <p>When an {@link io.camunda.zeebe.db.impl.inmemory.InMemoryColumnFamily} reads into such a
 * target, it should bypass type-specific {@link DbValue#copyTo(DbValue)} implementations and fall
 * back to serialization + {@link DbValue#wrap}.
 */
public interface UntypedDbValueTarget {}
