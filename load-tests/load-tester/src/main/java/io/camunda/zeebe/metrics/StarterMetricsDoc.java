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
  }
}
