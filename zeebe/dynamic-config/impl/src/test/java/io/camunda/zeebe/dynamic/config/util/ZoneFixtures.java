/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ZoneFixtures {

  // zones
  public static final String ZONE_A = "zone-a";
  public static final String ZONE_B = "zone-b";
  public static final String ZONE_C = "zone-c";

  // bare node ids
  public static final MemberId BARE_0 = MemberId.from(0);
  public static final MemberId BARE_1 = MemberId.from(1);
  public static final MemberId BARE_2 = MemberId.from(2);
  public static final MemberId BARE_3 = MemberId.from(3);
  // zone A
  public static final MemberId ZONE_A_0 = MemberId.from(ZONE_A, 0);
  public static final MemberId ZONE_A_1 = MemberId.from(ZONE_A, 1);
  public static final MemberId ZONE_A_2 = MemberId.from(ZONE_A, 2);
  public static final List<MemberId> ZONE_A_NODES = List.of(ZONE_A_0, ZONE_A_1, ZONE_A_2);
  // zone B
  public static final MemberId ZONE_B_0 = MemberId.from(ZONE_B, 0);
  public static final MemberId ZONE_B_1 = MemberId.from(ZONE_B, 1);
  public static final MemberId ZONE_B_2 = MemberId.from(ZONE_B, 2);
  // Zone configs
  public static final List<ZoneSpec> SINGLE_REGION = List.of(new ZoneSpec(ZONE_A, 3, 100));
  public static final List<ZoneSpec> DUAL_REGION =
      List.of(new ZoneSpec(ZONE_A, 2, 100), new ZoneSpec(ZONE_B, 2, 100));

  public static Set<MemberId> bareNodes(final int n) {
    return IntStream.range(0, n).mapToObj(MemberId::from).collect(Collectors.toSet());
  }
}
