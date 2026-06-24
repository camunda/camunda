/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.protocol;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

@SuppressWarnings("NullableProblems")
public enum SwimMembershipProtocolMetricsDoc implements ExtendedMeterDocumentation {
  /**
   * Member's Incarnation number. This metric shows the incarnation number of each member in the
   * several nodes. This is useful to observe state propagation of each member information.
   */
  MEMBERS_INCARNATION_NUMBER {
    @Override
    public String getName() {
      return "zeebe.smp.members.incarnation.number";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Member's Incarnation number. This metric shows the incarnation number of each "
          + "member in the several nodes. This is useful to observe state propagation of each"
          + "member information.";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {};
    }
  };

  public enum SwimKeyNames implements KeyName {
    MEMBER_ID {
      @Override
      public String asString() {
        return "memberId";
      }
    }
  }
}
