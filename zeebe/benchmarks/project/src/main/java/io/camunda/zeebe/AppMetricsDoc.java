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
package io.camunda.zeebe;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.core.instrument.Meter.Type;

/** Metrics shared across all app types (Starter and Worker). */
public enum AppMetricsDoc implements ExtendedMeterDocumentation {
  /**
   * A gauge set to 1 when the client successfully connects to the gateway (i.e. after the first
   * successful topology request), and 0 otherwise. This metric is used by the verification workflow
   * to confirm that the client is connected, regardless of whether the client uses gRPC or REST.
   */
  CONNECTED {
    @Override
    public String getDescription() {
      return "Set to 1 when the client successfully connects to the gateway (topology received), 0 otherwise.";
    }

    @Override
    public String getName() {
      return "app.connected";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }
  };
}
