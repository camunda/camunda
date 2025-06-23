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
package io.camunda.spring.client.jobhandling.result;

import io.camunda.client.api.command.CreateDocumentBatchCommandStep1.CreateDocumentBatchCommandStep2;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.DocumentReferenceBatchResponse;
import io.camunda.client.api.worker.JobClient;
import java.util.Map;
import java.util.function.Function;

/**
 * This handler will be invoked as soon as a job worker result contains a {@link
 * io.camunda.spring.client.jobhandling.DocumentContext} that fails to upload all documents in a
 * batch.
 */
public interface DocumentResultProcessorFailureHandlingStrategy {

  /**
   * The method that will be invoked on failure. This method can throw runtime exception, including
   * {@link io.camunda.spring.client.exception.JobError} and {@link
   * io.camunda.spring.client.exception.BpmnError} that will be respected and processed by a {@link
   * io.camunda.spring.client.jobhandling.JobExceptionHandlingStrategy}
   *
   * @param context the context being available for handling the failure
   * @throws RuntimeException the exception that may be thrown
   */
  void handleFailure(FailureHandlingContext context) throws RuntimeException;

  record FailureHandlingContext(
      ActivatedJob activatedJob,
      JobClient jobClient,
      DocumentReferenceBatchResponse response,
      Map<String, Function<CreateDocumentBatchCommandStep2, CreateDocumentBatchCommandStep2>>
          failedDocumentBuilders) {}
}
