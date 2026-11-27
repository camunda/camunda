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

import io.micrometer.common.docs.KeyName;

/** Tag keys shared by the metrics that report the result of a request to the cluster. */
public enum ResultMetricKeyNames implements KeyName {

  /** The outcome of a request or of its answer */
  OUTCOME {
    @Override
    public String asString() {
      return "outcome";
    }
  },

  /** The error type of a failed answer, e.g. {@code grpc_resource_exhausted} or {@code http_503} */
  ERROR {
    @Override
    public String asString() {
      return "error";
    }
  }
}
