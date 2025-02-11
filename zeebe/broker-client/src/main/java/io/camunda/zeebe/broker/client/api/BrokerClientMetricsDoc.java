/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.client.api;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

/** Provides metrics relating to the communication between brokers and gateways. */
@SuppressWarnings("NullableProblems")
public enum BrokerClientMetricsDoc implements ExtendedMeterDocumentation {
  /** Latency of round-trip from gateway to broker */
  REQUEST_LATENCY {
    @Override
    public String getDescription() {
      return "Latency of round-trip from gateway to broker";
    }

    @Override
    public String getName() {
      return "zeebe.gateway.request.latency";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {RequestKeyNames.REQUEST_TYPE};
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** Number of failed requests */
  FAILED_REQUESTS {
    @Override
    public String getDescription() {
      return "Number of failed requests";
    }

    @Override
    public String getName() {
      return "zeebe.gateway.failed.requests";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return RequestKeyNames.values();
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** Number of requests */
  TOTAL_REQUESTS {
    @Override
    public String getDescription() {
      return "Number of requests";
    }

    @Override
    public String getName() {
      return "zeebe.gateway.total.requests";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {RequestKeyNames.REQUEST_TYPE};
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** The partition role of the broker. Possible values are those at {@link PartitionRoleValues} */
  PARTITION_ROLE {
    @Override
    public String getDescription() {
      return "The partition role of the broker. 0 = Follower, 3 = Leader.";
    }

    @Override
    public String getName() {
      return "zeebe.gateway.topology.partition.roles";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getKeyNames() {
      return TopologyKeyNames.values();
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  };

  /** Additional error codes that are not produced by brokers, but from the broker client itself. */
  public enum AdditionalErrorCodes {
    /**
     * If a request returns this, then the gateway did not know of this partition. This could mean
     * it's not known in the topology yet, or that it simply does not exist. If the issue persists,
     * check your configuration.
     */
    PARTITION_NOT_FOUND,
    /**
     * The gateway is not topology aware yet. This is normal at startup, but persistence means your
     * gateway is badly configured, or your network is.
     */
    NO_TOPOLOGY,
    /** The request timed out on the client side. */
    TIMEOUT,
    UNKNOWN
  }

  /** Possible key names for the broker client metrics. */
  public enum RequestKeyNames implements KeyName {
    /** The type of the request, sometimes referred to as its topic. */
    REQUEST_TYPE {
      @Override
      public String asString() {
        return "requestType";
      }
    },

    /**
     * The error type when a request from a gateway to a broker fails; one of {@link
     * io.camunda.zeebe.protocol.record.ErrorCode} or {@link AdditionalErrorCodes}.
     */
    ERROR {
      @Override
      public String asString() {
        return "error";
      }
    }
  }

  /** Possibly key names for the topology metrics */
  public enum TopologyKeyNames implements KeyName {
    /** A tag/label which specifies the broker; possible values are the broker's node ID. */
    BROKER {
      @Override
      public String asString() {
        return "broker";
      }
    }
  }

  public enum PartitionRoleValues {
    LEADER(3),
    FOLLOWER(0);

    private final int value;

    PartitionRoleValues(final int value) {
      this.value = value;
    }

    public int value() {
      return value;
    }
  }
}
