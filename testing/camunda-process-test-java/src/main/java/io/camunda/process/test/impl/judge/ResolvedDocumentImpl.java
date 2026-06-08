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
package io.camunda.process.test.impl.judge;

import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.process.test.api.judge.ResolvedDocument;

public final class ResolvedDocumentImpl implements ResolvedDocument {

  private final String documentId;
  private final String storeId;
  private final String contentHash;
  private final String fileName;
  private final String contentType;
  private final byte[] content;

  public ResolvedDocumentImpl(final DocumentReferenceResponse reference, final byte[] content) {
    this.documentId = reference.getDocumentId();
    this.storeId = reference.getStoreId();
    this.contentHash = reference.getContentHash();
    this.fileName = reference.getMetadata() != null ? reference.getMetadata().getFileName() : null;
    this.contentType =
        reference.getMetadata() != null ? reference.getMetadata().getContentType() : null;
    this.content = content;
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
  public String getContentHash() {
    return contentHash;
  }

  @Override
  public String getFileName() {
    return fileName;
  }

  @Override
  public String getContentType() {
    return contentType;
  }

  @Override
  public byte[] getContent() {
    return content;
  }
}
