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

import io.camunda.client.api.command.CreateDocumentBatchCommandStep1;
import io.camunda.client.api.command.CreateDocumentBatchCommandStep1.CreateDocumentBatchCommandStep2;
import io.camunda.client.api.worker.JobClient;
import io.camunda.spring.client.jobhandling.DocumentContext;
import java.util.function.Function;

/**
 * A builder to create a {@link DocumentContext} for a job worker result.
 *
 * <p>The {@link DefaultResultProcessor} will use the context to execute a {@link
 * JobClient#newCreateDocumentBatchCommand()} and set the references as process variable.
 */
public interface ResultDocumentContextBuilder {

  /**
   * @return the {@link DocumentContext} that will be used for further processing
   */
  DocumentContext build();

  /**
   * Applies a store id to the batch. See {@link
   * io.camunda.client.api.command.CreateDocumentBatchCommandStep1#storeId(String)}.
   *
   * @param storeId the store id to save all documents in the batch to
   * @return the context builder
   */
  ResultDocumentContextBuilder storeId(String storeId);

  /**
   * Adds a document to the batch using the provided function that is applied to the builder after
   * {@link CreateDocumentBatchCommandStep1#addDocument()} and before {@link
   * CreateDocumentBatchCommandStep2#done()}
   *
   * @param fileName the fileName to apply to the command builder
   * @param documentBuilder the function to apply to the command builder, the {@link
   *     CreateDocumentBatchCommandStep2#fileName(String)} can be omitted here
   * @return the context builder
   */
  ResultDocumentContextBuilder addDocument(
      String fileName,
      Function<CreateDocumentBatchCommandStep2, CreateDocumentBatchCommandStep2> documentBuilder);
}
