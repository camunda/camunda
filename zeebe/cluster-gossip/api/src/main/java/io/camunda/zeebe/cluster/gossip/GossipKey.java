/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip;

/**
 * Describes a gossiped key: its cluster-wide unique name, its scope and how its value is encoded.
 * Declare a key once, typically as a constant, and share it between writers and readers.
 *
 * @param <T> the type of the value
 */
public sealed interface GossipKey<T> permits GlobalKey, MemberKey {

  /** The name of the key, unique across the cluster and across both scopes. */
  String name();

  /** Encodes and decodes the value. */
  GossipCodec<T> codec();

  /** Declares a GLOBAL key. See {@link GlobalKey}. */
  static <T> GlobalKey<T> global(
      final String name,
      final GossipCodec<T> codec,
      final MergeStrategy<T> mergeStrategy,
      final T defaultValue) {
    return new GlobalKey<>(name, codec, mergeStrategy, defaultValue);
  }

  /** Declares a MEMBER key. See {@link MemberKey}. */
  static <T> MemberKey<T> member(final String name, final GossipCodec<T> codec) {
    return new MemberKey<>(name, codec);
  }
}
