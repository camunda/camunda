/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.core.instrument.Meter.Type;

public enum StarterMetricsDoc implements ExtendedMeterDocumentation {

  /**
   * Total number of process instance start requests submitted by the starter. Incremented before
   * the create-instance call is issued, so this counts attempted submissions (including ones that
   * may fail before reaching the gateway) — a measure of "instances we asked the engine to start",
   * not "instances the engine created". Used by the quicker load test to compute throughput at the
   * end of a finite run.
   */
  PROCESS_INSTANCES_STARTED {
    @Override
    public String getDescription() {
      return "Total number of process instance start requests submitted by the starter.";
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
<<<<<<< HEAD
=======
  };

  public enum StarterMetricKeyNames implements KeyName {

    /** The name of the load-tester component emitting the metric */
    NAME {
      @Override
      public String asString() {
        return "name";
      }
    },

    /** The outcome of a scheduled start submission */
    OUTCOME {
      @Override
      public String asString() {
        return "outcome";
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
>>>>>>> 3cdd9645 (feat: add submission metrics)
  }
}
