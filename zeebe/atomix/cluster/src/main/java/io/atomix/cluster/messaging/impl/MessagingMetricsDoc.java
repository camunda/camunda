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
public enum MessagingMetricsDoc implements ExtendedMeterDocumentation {
  /** The time how long it takes to retrieve a response for a request */
  REQUEST_RESPONSE_LATENCY {
    @Override
    public String getName() {
      return "zeebe.messaging.request.response.latency";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {MessagingKeyNames.TOPIC};
    }

    @Override
    public String getDescription() {
      return "The time how long it takes to retrieve a response for a request";
    }

    @Override
    public String getBaseUnit() {
      return "ms";
    }
  },
  /** The size of the request, which has been sent */
  REQUEST_SIZE_IN_KB {
    @Override
    public String getName() {
      return "zeebe.messaging.request.size.kb";
    }

    @Override
    public Type getType() {
      return Type.DISTRIBUTION_SUMMARY;
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {MessagingKeyNames.ADDRESS, MessagingKeyNames.TOPIC};
    }

    @Override
    public String getDescription() {
      return "The size of the request, which has been sent";
    }

    @Override
    public String getBaseUnit() {
      return "KB";
    }

    @Override
    // FIXME
    public double[] getDistributionSLOs() {
      return super.getDistributionSLOs();
    }
  },
  /** Number of requests which has been sent to a certain address */
  REQUEST_COUNT {
    @Override
    public String getName() {
      return "zeebe.messaging.request.count";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {
        MessagingKeyNames.TYPE, MessagingKeyNames.ADDRESS, MessagingKeyNames.TOPIC
      };
    }

    @Override
    public String getDescription() {
      return "Number of requests which has been sent to a certain address";
    }
  },

  /** Number of responses which has been sent to a certain address */
  RESPONSE_COUNT {
    @Override
    public String getName() {
      return "zeebe.messaging.response.count";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {
        MessagingKeyNames.ADDRESS, MessagingKeyNames.TOPIC, MessagingKeyNames.OUTCOME
      };
    }

    @Override
    public String getDescription() {
      return "Number of responses which has been sent to a certain address";
    }
  },
  /** The count of inflight requests */
  IN_FLIGHT_REQUESTS {
    @Override
    public String getName() {
      return "zeebe.messaging.inflight.requests";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {MessagingKeyNames.ADDRESS, MessagingKeyNames.TOPIC};
    }

    @Override
    public String getDescription() {
      return "The count of inflight requests";
    }
  };

  // FIXME add documentation to the names
  enum MessagingKeyNames implements KeyName {
    TOPIC {
      @Override
      public String asString() {
        return "topic";
      }
    },
    ADDRESS {
      @Override
      public String asString() {
        return "address";
      }
    },

    TYPE {
      @Override
      public String asString() {
        return "type";
      }
    },
    OUTCOME {
      @Override
      public String asString() {
        return "outcome";
      }
    };
  }
}
