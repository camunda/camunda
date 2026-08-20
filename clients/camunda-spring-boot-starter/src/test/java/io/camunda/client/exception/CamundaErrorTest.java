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
package io.camunda.client.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class CamundaErrorTest {

  @Nested
  class BpmnErrorTest {
    @Test
    void shouldCreateBpmnErrorSimple() {
      final BpmnError bpmnError = CamundaError.bpmnError("code", "message");
      assertThat(bpmnError).isNotNull();
      assertThat(bpmnError.getErrorCode()).isEqualTo("code");
      assertThat(bpmnError.getErrorMessage()).isEqualTo("message");
      assertThat(bpmnError.getVariables()).isNull();
      assertThat(bpmnError.getCause()).isNull();
    }

    @Test
    void shouldCreateBpmnErrorWithVariables() {
      final Map<String, Object> variables = new HashMap<>();
      final BpmnError bpmnError = CamundaError.bpmnError("code", "message", variables);
      assertThat(bpmnError).isNotNull();
      assertThat(bpmnError.getErrorCode()).isEqualTo("code");
      assertThat(bpmnError.getErrorMessage()).isEqualTo("message");
      assertThat(bpmnError.getVariables()).isEqualTo(variables);
      assertThat(bpmnError.getCause()).isNull();
    }

    @Test
    void shouldCreateBpmnErrorWithVariablesAndCause() {
      final Map<String, Object> variables = new HashMap<>();
      final Exception cause = new Exception("cause");
      final BpmnError bpmnError = CamundaError.bpmnError("code", "message", variables, cause);
      assertThat(bpmnError).isNotNull();
      assertThat(bpmnError.getErrorCode()).isEqualTo("code");
      assertThat(bpmnError.getErrorMessage()).isEqualTo("message");
      assertThat(bpmnError.getVariables()).isEqualTo(variables);
      assertThat(bpmnError.getCause()).isEqualTo(cause);
    }
  }

  @Nested
  class JobErrorTest {
    @Test
    void shouldCreateJobErrorSimple() {
      final JobError jobError = CamundaError.jobError("message");
      assertThat(jobError).isNotNull();
      assertThat(jobError.getMessage()).isEqualTo("message");
      assertThat(jobError.getVariables()).isNull();
      assertThat(jobError.getRetries()).isNull();
      assertThat(jobError.getRetryBackoff().apply(null)).isNull();
      assertThat(jobError.getCause()).isNull();
    }

    @Test
    void shouldCreateJobErrorVariables() {
      final Map<String, Object> variables = new HashMap<>();
      final JobError jobError = CamundaError.jobError("message", variables);
      assertThat(jobError).isNotNull();
      assertThat(jobError.getMessage()).isEqualTo("message");
      assertThat(jobError.getVariables()).isEqualTo(variables);
      assertThat(jobError.getRetries()).isNull();
      assertThat(jobError.getRetryBackoff().apply(null)).isNull();
      assertThat(jobError.getCause()).isNull();
    }

    @Test
    void shouldCreateJobErrorVariablesAndRetries() {
      final Map<String, Object> variables = new HashMap<>();
      final int retries = 2;
      final JobError jobError = CamundaError.jobError("message", variables, 2);
      assertThat(jobError).isNotNull();
      assertThat(jobError.getMessage()).isEqualTo("message");
      assertThat(jobError.getVariables()).isEqualTo(variables);
      assertThat(jobError.getRetries()).isEqualTo(retries);
      assertThat(jobError.getRetryBackoff().apply(retries)).isNull();
      assertThat(jobError.getCause()).isNull();
    }

    @Test
    void shouldCreateJobErrorVariablesAndRetriesAndTimeout() {
      final Map<String, Object> variables = new HashMap<>();
      final int retries = 2;
      final Duration timeout = Duration.ofSeconds(10);
      final JobError jobError = CamundaError.jobError("message", variables, 2, timeout);
      assertThat(jobError).isNotNull();
      assertThat(jobError.getMessage()).isEqualTo("message");
      assertThat(jobError.getVariables()).isEqualTo(variables);
      assertThat(jobError.getRetries()).isEqualTo(retries);
      assertThat(jobError.getRetryBackoff().apply(retries)).isEqualTo(timeout);
      assertThat(jobError.getCause()).isNull();
    }

    @Test
    void shouldCreateJobErrorVariablesAndRetriesAndTimeoutAndCause() {
      final Map<String, Object> variables = new HashMap<>();
      final int retries = 2;
      final Duration timeout = Duration.ofSeconds(10);
      final Exception cause = new Exception("cause");
      final JobError jobError = CamundaError.jobError("message", variables, 2, timeout, cause);
      assertThat(jobError).isNotNull();
      assertThat(jobError.getMessage()).isEqualTo("message");
      assertThat(jobError.getVariables()).isEqualTo(variables);
      assertThat(jobError.getRetries()).isEqualTo(retries);
      assertThat(jobError.getRetryBackoff().apply(retries)).isEqualTo(timeout);
      assertThat(jobError.getCause()).isEqualTo(cause);
    }

    @Test
    void shouldCreateJobErrorVariablesAndRetriesAndTimeoutFunctionAndCause() {
      final Map<String, Object> variables = new HashMap<>();
      final int retries = 2;
      final Duration timeout = Duration.ofSeconds(10);
      final Exception cause = new Exception("cause");
      final JobError jobError = CamundaError.jobError("message", variables, 2, r -> timeout, cause);
      assertThat(jobError).isNotNull();
      assertThat(jobError.getMessage()).isEqualTo("message");
      assertThat(jobError.getVariables()).isEqualTo(variables);
      assertThat(jobError.getRetries()).isEqualTo(retries);
      assertThat(jobError.getRetryBackoff().apply(retries)).isEqualTo(timeout);
      assertThat(jobError.getCause()).isEqualTo(cause);
    }
  }
}
