/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered;

/**
 * One entry in a layered-store layer: a present value or a tombstone.
 *
 * @param key the serialized key, never null
 * @param value the serialized value; {@code null} iff this entry is a tombstone (a buffered delete
 *     that must hide any value in the layers below)
 * @param flushed whether the durable delegate holds (or, via a segment currently being persisted,
 *     is about to hold) a version of this key. A delete of a never-flushed put may be absorbed —
 *     the pair annihilates without ever reaching the delegate — while a delete of a flushed key
 *     must reach the delegate as a tombstone. See {@link LayeredKeyValueStore} for the soundness
 *     argument.
 */
public record Entry(byte[] key, byte[] value, boolean flushed) {

  public boolean tombstone() {
    return value == null;
  }
}
