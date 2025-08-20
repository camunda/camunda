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
package io.camunda.client.impl.response;

import io.camunda.client.api.response.DocumentMetadata;
import io.camunda.client.api.response.DocumentReferenceResponse;

public class DocumentReferenceResponseImpl implements DocumentReferenceResponse {

  private final String documentId;
  private final String storeId;
  private final DocumentMetadata metadata;

  public DocumentReferenceResponseImpl(
      final io.camunda.client.protocol.rest.DocumentReference documentReference) {
    documentId = documentReference.getDocumentId();
    storeId = documentReference.getStoreId();
    metadata = new DocumentMetadataImpl(documentReference.getMetadata());
  }

  @Override
  public String getDocumentId() {
    return documentId;
  }

  @Override
  public String getStoreId() {
    return storeId;
  }

  @Override
  public DocumentMetadata getMetadata() {
    return metadata;
  }
}
