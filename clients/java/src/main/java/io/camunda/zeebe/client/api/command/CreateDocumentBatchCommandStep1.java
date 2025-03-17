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
package io.camunda.zeebe.client.api.command;

import io.camunda.zeebe.client.api.command.DocumentBuilderStep1.DocumentBuilderStep2;
import io.camunda.zeebe.client.api.response.DocumentReferenceBatchResponse;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.command.CreateDocumentBatchCommandStep1}
 */
@Deprecated
public interface CreateDocumentBatchCommandStep1
    extends FinalCommandStep<DocumentReferenceBatchResponse> {

  /**
   * Sets the store ID. If not set, the default store will be used.
   *
   * <p>The store ID is the identifier of the document store where the document should be stored.
   * Documents with the same ID can exist in different stores.
   *
   * @param storeId the store ID
   */
  CreateDocumentBatchCommandStep1 storeId(String storeId);

  /**
   * Sets the process definition that the document is associated with.
   *
   * @param processDefinitionId the process definition ID
   */
  CreateDocumentBatchCommandStep1 processDefinitionId(String processDefinitionId);

  /**
   * Sets the process instance key that the document is associated with.
   *
   * @param processInstanceKey the process instance key
   */
  CreateDocumentBatchCommandStep1 processInstanceKey(long processInstanceKey);

  /** Starts the creation of a new document in a batch. */
  CreateDocumentBatchCommandStep2 addDocument();

  interface CreateDocumentBatchCommandStep2 extends DocumentBuilderStep1, DocumentBuilderStep2 {

    @Override
    CreateDocumentBatchCommandStep2 content(InputStream content);

    @Override
    CreateDocumentBatchCommandStep2 content(byte[] content);

    @Override
    CreateDocumentBatchCommandStep2 content(String content);

    @Override
    CreateDocumentBatchCommandStep2 contentType(String contentType);

    @Override
    CreateDocumentBatchCommandStep2 fileName(String name);

    @Override
    CreateDocumentBatchCommandStep2 timeToLive(Duration timeToLive);

    @Override
    CreateDocumentBatchCommandStep2 customMetadata(String key, Object value);

    @Override
    CreateDocumentBatchCommandStep2 customMetadata(Map<String, Object> customMetadata);

    /** Finishes the creation of the current document and returns to the parent step. */
    CreateDocumentBatchCommandStep1 done();
  }
}
