/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import io.atomix.cluster.MemberId;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Provide;
import net.jqwik.api.domains.DomainContextBase;

/** Arbitraries generating {@link MemberId}s that are valid according to its constructors. */
public final class MemberIdArbitraries extends DomainContextBase {

  @Provide
  Arbitrary<MemberId> memberId() {
    final var nonZoned = Arbitraries.integers().between(0, 50).map(MemberId::from);
    final var zone = Arbitraries.of("zone-a", "zone-b", "zone-c", "region1");
    final var nodeIdx = Arbitraries.integers().between(0, 50);
    final var zoned = Combinators.combine(zone, nodeIdx).as(MemberId::from);
    return Arbitraries.oneOf(nonZoned, zoned);
  }

  @Provide
  Arbitrary<Set<MemberId>> memberIds() {
    return memberId().set().ofMaxSize(20);
  }
}
