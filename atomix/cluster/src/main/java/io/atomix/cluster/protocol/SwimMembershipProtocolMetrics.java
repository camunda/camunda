/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.cluster.protocol;

import io.prometheus.client.Gauge;

final class SwimMembershipProtocolMetrics {

  private static final Gauge MEMBERS_INCARNATION_NUMBER =
      Gauge.build()
          .namespace("zeebe")
          .name("smp_members_incarnation_number")
          .help(
              "Member's Incarnation number. This metric shows the incarnation number of each "
                  + "member in the several nodes. This is useful to observe state propagation of each"
                  + "member information.")
          .labelNames("memberId")
          .register();

  static void updateMemberIncarnationNumber(final String member, final long incarnationNumber) {
    MEMBERS_INCARNATION_NUMBER.labels(member).set(incarnationNumber);
  }
}
