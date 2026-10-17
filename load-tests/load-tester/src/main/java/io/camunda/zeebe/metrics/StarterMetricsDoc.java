/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

public enum StarterMetricsDoc implements ExtendedMeterDocumentation {

  /**
   * An "info"-style metric: a gauge constantly set to 1 whose tags carry static configuration — the
   * {@code name} (load-tester component, e.g. "starter"), the {@code process_id} (BPMN process id
   * loaded by the starter) and the {@code nb_threads} (number of starter threads). Lets dashboards
   * surface how a load test is configured, e.g. {@code avg(client_info) by (process_id)}.
   */
  CLIENT_INFO {
    private static final KeyName[] KEY_NAMES =
        new KeyName[] {
          StarterMetricKeyNames.NAME,
          StarterMetricKeyNames.PROCESS_ID,
          StarterMetricKeyNames.NB_THREADS
        };

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }

    @Override
    public String getDescription() {
      return "The information about the client.";
    }

    @Override
    public String getName() {
      return "client.info";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }
  },

  /**
   * Total number of answered process instance start requests by their {@code outcome}: {@code
   * success} or {@code failure}. Failures also carry the {@code error} type, e.g. {@code
   * grpc_resource_exhausted} or {@code http_503}; successes use {@code none}. Counted when the
   * answer arrives, so requests without an answer are not included, they show up in the in-flight
   * gauge. Used by the quicker load test to compute throughput at the end of a finite run.
   */
  PROCESS_INSTANCES_STARTED {
    private static final KeyName[] KEY_NAMES =
        new KeyName[] {StarterMetricKeyNames.OUTCOME, StarterMetricKeyNames.ERROR};

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }

    @Override
    public String getDescription() {
      return "Total number of answered process instance start requests by outcome and error type.";
    }

    @Override
    public String getName() {
      return "starter.process.instances.started";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }
  },

  /**
   * Total number of scheduled process instance start submissions by their {@code outcome}: {@code
   * submitted} when the request was sent, {@code skipped} when it was dropped because the maximum
   * number of in-flight requests was reached. A non-zero skipped rate means the target rate is not
   * sustained, e.g. because the cluster responds slower than the starter schedules requests.
   */
  PROCESS_INSTANCES_SUBMISSIONS {
    private static final KeyName[] KEY_NAMES = new KeyName[] {StarterMetricKeyNames.OUTCOME};

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }

    @Override
    public String getDescription() {
      return "Total number of scheduled process instance start submissions by outcome.";
    }

    @Override
    public String getName() {
      return "starter.process.instances.submissions";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }
  },

  /** Number of process instance start requests that were sent and have no answer yet. */
  PROCESS_INSTANCES_IN_FLIGHT {
    @Override
    public String getDescription() {
      return "Number of process instance start requests sent and still awaiting an answer.";
    }

    @Override
    public String getName() {
      return "starter.process.instances.in.flight";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }
  },

  /**
   * Set to 1 when the starter has finished its instance-creation loop (either because the
   * configured duration-limit elapsed or because it was otherwise stopped). Stays at 0 while the
   * starter is actively creating instances. Lets external watchers (e.g. the quicker load test
   * workflow) detect completion without relying on pod phase — Spring Boot's WebFlux server keeps
   * the JVM alive after the CommandLineRunner returns, so the pod stays Running.
   */
  RUN_FINISHED {
    @Override
    public String getDescription() {
      return "1 once the starter has finished its instance-creation loop, 0 otherwise.";
    }

    @Override
    public String getName() {
      return "starter.run.finished";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }
  };

  public enum StarterMetricKeyNames implements KeyName {

    /** The name of the load-tester component emitting the metric */
    NAME {
      @Override
      public String asString() {
        return "name";
      }
    },

    /** The outcome of a start submission or of its answer */
    OUTCOME {
      @Override
      public String asString() {
        return "outcome";
      }
    },

    /**
     * The error type of a failed answer, e.g. {@code grpc_resource_exhausted} or {@code http_503}
     */
    ERROR {
      @Override
      public String asString() {
        return "error";
      }
    },

    /** The number of threads configured in the starter */
    NB_THREADS {
      @Override
      public String asString() {
        return "nb_threads";
      }
    },

    /** The ID of the partition associated to the metric */
    PARTITION {
      @Override
      public String asString() {
        return "partition";
      }
    },

    /** The BPMN process id loaded by the starter */
    PROCESS_ID {
      @Override
      public String asString() {
        return "process_id";
      }
    },
  }
}
