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

import java.time.Duration;
import java.util.function.Function;

public abstract class CamundaError extends RuntimeException {
  protected CamundaError(final String message, final Throwable cause) {
    super(message, cause);
  }

  /**
   * Creates a bpmn error from the provided parameters. For more information, see {@link
   * io.camunda.client.CamundaClient#newThrowErrorCommand(long)}
   *
   * @param errorCode the error code for the throw error command
   * @param errorMessage the error message for the throw error command
   * @param variables the variables for the throw error command
   * @param cause the cause of the issue
   * @return the error that can be thrown from inside a {@link
   *     io.camunda.client.annotation.JobWorker}
   */
  public static BpmnError bpmnError(
      final String errorCode,
      final String errorMessage,
      final Object variables,
      final Throwable cause) {
    return new BpmnError(errorCode, errorMessage, variables, cause);
  }

  /**
   * Creates a bpmn error from the provided parameters. For more information, see {@link
   * io.camunda.client.CamundaClient#newThrowErrorCommand(long)}
   *
   * @param errorCode the error code for the throw error command
   * @param errorMessage the error message for the throw error command
   * @param variables the variables for the throw error command
   * @return the error that can be thrown from inside a {@link
   *     io.camunda.client.annotation.JobWorker}
   */
  public static BpmnError bpmnError(
      final String errorCode, final String errorMessage, final Object variables) {
    return bpmnError(errorCode, errorMessage, variables, null);
  }

  /**
   * Creates a bpmn error from the provided parameters. For more information, see {@link
   * io.camunda.client.CamundaClient#newThrowErrorCommand(long)}
   *
   * @param errorCode the error code for the throw error command
   * @param errorMessage the error message for the throw error command
   * @return the error that can be thrown from inside a {@link
   *     io.camunda.client.annotation.JobWorker}
   */
  public static BpmnError bpmnError(final String errorCode, final String errorMessage) {
    return bpmnError(errorCode, errorMessage, null, null);
  }

  /**
   * Creates a job error from the provided parameters. For more information, see {@link
   * io.camunda.client.CamundaClient#newFailCommand(long)}
   *
   * @param errorMessage the message for the fail job command
   * @return the error that can be thrown from inside a {@link
   *     io.camunda.client.annotation.JobWorker}
   */
  public static JobError jobError(final String errorMessage) {
    return jobError(errorMessage, null, null, (Duration) null, null);
  }

  /**
   * Creates a job error from the provided parameters. For more information, see {@link
   * io.camunda.client.CamundaClient#newFailCommand(long)}
   *
   * @param errorMessage the message for the fail job command
   * @param variables the variables for the fail job command
   * @return the error that can be thrown from inside a {@link
   *     io.camunda.client.annotation.JobWorker}
   */
  public static JobError jobError(final String errorMessage, final Object variables) {
    return jobError(errorMessage, variables, null, (Duration) null, null);
  }

  /**
   * Creates a job error from the provided parameters. For more information, see {@link
   * io.camunda.client.CamundaClient#newFailCommand(long)}
   *
   * @param errorMessage the message for the fail job command
   * @param variables the variables for the fail job command
   * @param retries the retries for the fail job command
   * @return the error that can be thrown from inside a {@link
   *     io.camunda.client.annotation.JobWorker}
   */
  public static JobError jobError(
      final String errorMessage, final Object variables, final Integer retries) {
    return jobError(errorMessage, variables, retries, (Duration) null, null);
  }

  /**
   * Creates a job error from the provided parameters. For more information, see {@link
   * io.camunda.client.CamundaClient#newFailCommand(long)}
   *
   * @param errorMessage the message for the fail job command
   * @param variables the variables for the fail job command
   * @param retries the retries for the fail job command
   * @param retryBackoff the retry backoff for the fail job command
   * @return the error that can be thrown from inside a {@link
   *     io.camunda.client.annotation.JobWorker}
   */
  public static JobError jobError(
      final String errorMessage,
      final Object variables,
      final Integer retries,
      final Duration retryBackoff) {
    return jobError(errorMessage, variables, retries, retryBackoff, null);
  }

  /**
   * Creates a job error from the provided parameters. For more information, see {@link
   * io.camunda.client.CamundaClient#newFailCommand(long)}
   *
   * @param errorMessage the message for the fail job command
   * @param variables the variables for the fail job command
   * @param retries the retries for the fail job command
   * @param retryBackoff the retry backoff for the fail job command
   * @param cause the cause of the issue, will be appended to the stacktrace
   * @return the error that can be thrown from inside a {@link
   *     io.camunda.client.annotation.JobWorker}
   */
  public static JobError jobError(
      final String errorMessage,
      final Object variables,
      final Integer retries,
      final Duration retryBackoff,
      final Throwable cause) {
    return new JobError(errorMessage, variables, retries, r -> retryBackoff, cause);
  }

  /**
   * Creates a job error from the provided parameters. For more information, see {@link
   * io.camunda.client.CamundaClient#newFailCommand(long)}
   *
   * @param errorMessage the message for the fail job command
   * @param variables the variables for the fail job command
   * @param retries the retries for the fail job command
   * @param retryBackoff a function that supplies the new retries as parameter and expects the retry
   *     backoff for the fail job command
   * @return the error that can be thrown from inside a {@link
   *     io.camunda.client.annotation.JobWorker}
   */
  public static JobError jobError(
      final String errorMessage,
      final Object variables,
      final Integer retries,
      final Function<Integer, Duration> retryBackoff) {
    return jobError(errorMessage, variables, retries, retryBackoff, null);
  }

  /**
   * Creates a job error from the provided parameters. For more information, see {@link
   * io.camunda.client.CamundaClient#newFailCommand(long)}
   *
   * @param errorMessage the message for the fail job command
   * @param variables the variables for the fail job command
   * @param retries the retries for the fail job command
   * @param retryBackoff a function that supplies the new retries as parameter and expects the retry
   *     backoff for the fail job command
   * @param cause the cause of the issue
   * @return the error that can be thrown from inside a {@link
   *     io.camunda.client.annotation.JobWorker}
   */
  public static JobError jobError(
      final String errorMessage,
      final Object variables,
      final Integer retries,
      final Function<Integer, Duration> retryBackoff,
      final Throwable cause) {
    return new JobError(errorMessage, variables, retries, retryBackoff, cause);
  }
}
