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
 * A key with one value per member, written only by that member. A value lives for one run of its
 * member.
 *
 * @param name the cluster-wide unique name of the key
 * @param codec encodes and decodes the value
 * @param <T> the type of the value
 */
public record MemberKey<T>(String name, GossipCodec<T> codec) implements GossipKey<T> {

  public MemberKey {
    GossipKeys.requireValidName(name);
    Objects.requireNonNull(codec, "codec");
  }
}
