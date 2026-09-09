/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import static dev.hegel.Generators.composite;
import static dev.hegel.Generators.integers;
import static dev.hegel.Generators.oneOf;
import static dev.hegel.Generators.sampledFrom;
import static dev.hegel.Generators.sets;

import dev.hegel.Generator;
import io.atomix.cluster.MemberId;
import java.util.Set;

/** Generators for {@link MemberId}s that are valid according to its constructors. */
public final class MemberIdArbitraries {

  private MemberIdArbitraries() {}

  public static Generator<MemberId> memberId() {
    final Generator<MemberId> nonZoned = integers().min(0).max(50).map(MemberId::from);
    final Generator<MemberId> zoned =
        composite(
            tc ->
                MemberId.from(
                    tc.draw(sampledFrom("zone-a", "zone-b", "zone-c", "region1")),
                    tc.draw(integers().min(0).max(50))));
    return oneOf(nonZoned, zoned);
  }

  public static Generator<Set<MemberId>> memberIds() {
    return sets(memberId()).maxSize(20);
  }
}
