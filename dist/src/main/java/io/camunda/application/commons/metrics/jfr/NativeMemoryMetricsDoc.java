/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.metrics.jfr;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

/** Documentation for metrics related to memory tracking which includes native memory. */
@SuppressWarnings("NullableProblems")
public enum NativeMemoryMetricsDoc implements ExtendedMeterDocumentation {
  /** The current resident set size as observed by the JVM */
  RSS {
    @Override
    public String getDescription() {
      return "The current resident set size as observed by the JVM";
    }

    @Override
    public String getName() {
      return "camunda.memory.rss";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getJfrEventName() {
      return "jdk.ResidentSetSize";
    }
  },

  /** The current resident set size as observed by the JVM */
  RSS_PEAK {
    @Override
    public String getDescription() {
      return "The peak resident set size as observed so far by the JVM";
    }

    @Override
    public String getName() {
      return "camunda.memory.rss.peak";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getJfrEventName() {
      return "jdk.ResidentSetSize";
    }
  },

  /** The reserved and committed memory usage of native memory usage, split by type */
  NMT_USAGE {
    @Override
    public String getDescription() {
      return "The reserved and committed memory usage of native memory usage, split by type";
    }

    @Override
    public String getName() {
      return "camunda.memory.nmt";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getKeyNames() {
      return NativeMemoryUsageKeys.values();
    }

    @Override
    public String getJfrEventName() {
      return "jdk.NativeMemoryUsage";
    }
  },

  /** The total reserved and committed memory usage of native memory usage */
  NMT_USAGE_TOTAL {
    @Override
    public String getDescription() {
      return "The total reserved and committed memory usage of native memory usage";
    }

    @Override
    public String getName() {
      return "camunda.memory.nmt.total";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {NativeMemoryUsageKeys.STATE};
    }

    @Override
    public String getJfrEventName() {
      return "jdk.NativeMemoryUsageTotal";
    }
  };

  public abstract String getJfrEventName();

  @SuppressWarnings("NullableProblems")
  public enum NativeMemoryUsageKeys implements KeyName {
    /**
     * The specific native memory type; this can be things like "Heap", but also "Code", "GC",
     * "Compiler", etc.
     *
     * <p>See: <a
     * href="https://docs.oracle.com/javase/8/docs/technotes/guides/troubleshoot/tooldescr007.html">Oracle
     * documentation</a> on this
     */
    TYPE {
      @Override
      public String asString() {
        return "type";
      }
    },

    /** Specifies if this is the committed or reserved value for the given NMT type. */
    STATE {
      @Override
      public String asString() {
        return "state";
      }
    }
  }

  /** The possible values for the {@link NativeMemoryUsageKeys#STATE} tag. */
  public enum NativeMemoryValueType {
    RESERVED("reserved"),
    COMMITTED("committed");

    private final String value;

    NativeMemoryValueType(final String value) {
      this.value = value;
    }

    public String value() {
      return value;
    }
  }
}
