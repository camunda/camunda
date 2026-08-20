/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging.impl;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

@SuppressWarnings("NullableProblems")
public enum NettyDnsMetricsDoc implements ExtendedMeterDocumentation {
  /** Counts how often DNS queries fail with an error */
  ERROR {
    @Override
    public String getName() {
      return "zeebe.dns.error";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Counts how often DNS queries fail with an error";
    }
  },
  /** Counts how often DNS queries return an unsuccessful answer */
  FAILED {
    @Override
    public String getName() {
      return "zeebe.dns.failed";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Counts how often DNS queries return an unsuccessful answer";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {NettyDnsKeyName.CODE};
    }
  },
  /** Counts how often DNS queries are written */
  WRITTEN {
    @Override
    public String getName() {
      return "zeebe.dns.written";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Counts how often DNS queries are written ";
    }
  },
  /** Counts how often DNS queries are successful */
  SUCCESS {
    @Override
    public String getName() {
      return "zeebe.dns.success";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Counts how often DNS queries are successful";
    }
  };

  enum NettyDnsKeyName implements KeyName {
    CODE {
      @Override
      public String asString() {
        return "code";
      }
    }
  }
}
