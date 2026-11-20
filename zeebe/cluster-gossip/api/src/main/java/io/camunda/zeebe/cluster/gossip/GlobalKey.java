/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip;

import java.util.Objects;

/**
 * A key with one logical value cluster-wide, which any member may write.
 *
 * @param name the cluster-wide unique name of the key
 * @param codec encodes and decodes the value
 * @param mergeStrategy resolves concurrent writes
 * @param defaultValue returned while no value was received; never gossiped. Lets consumers avoid
 *     writing a default on startup, which under {@link MergeStrategy#lastWriterWins()} would
 *     override the value the rest of the cluster holds.
 * @param <T> the type of the value
 */
public record GlobalKey<T>(
    String name, GossipCodec<T> codec, MergeStrategy<T> mergeStrategy, T defaultValue)
    implements GossipKey<T> {

  public GlobalKey {
    GossipKeys.requireValidName(name);
    Objects.requireNonNull(codec, "codec");
    Objects.requireNonNull(mergeStrategy, "mergeStrategy");
    Objects.requireNonNull(defaultValue, "defaultValue");
  }
}
