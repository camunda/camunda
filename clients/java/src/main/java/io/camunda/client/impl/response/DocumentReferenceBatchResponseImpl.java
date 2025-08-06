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

import io.camunda.client.api.response.DocumentReferenceBatchResponse;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.protocol.rest.DocumentCreationBatchResponse;
import io.camunda.client.protocol.rest.DocumentCreationFailureDetail;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DocumentReferenceBatchResponseImpl implements DocumentReferenceBatchResponse {

  private final DocumentCreationBatchResponse response;

  public DocumentReferenceBatchResponseImpl(final DocumentCreationBatchResponse response) {
    this.response = response;
  }

  @Override
  public boolean isSuccessful() {
    return response.getFailedDocuments() == null || response.getFailedDocuments().isEmpty();
  }

  @Override
  public List<DocumentReferenceResponse> getCreatedDocuments() {
    if (response.getCreatedDocuments() == null) {
      return Collections.emptyList();
    }
    return response.getCreatedDocuments().stream()
        .map(DocumentReferenceResponseImpl::new)
        .collect(Collectors.toList());
  }

  @Override
  public List<FailedDocumentDetail> getFailedDocuments() {
    if (response.getFailedDocuments() == null) {
      return Collections.emptyList();
    }
    return response.getFailedDocuments().stream()
        .map(FailedDocumentDetailImpl::new)
        .collect(Collectors.toList());
  }

  public static class FailedDocumentDetailImpl implements FailedDocumentDetail {

    private final DocumentCreationFailureDetail failedDocumentDetail;

    public FailedDocumentDetailImpl(final DocumentCreationFailureDetail failedDocumentDetail) {
      this.failedDocumentDetail = failedDocumentDetail;
    }

    @Override
    public String getFileName() {
      return failedDocumentDetail.getFileName();
    }

    @Override
    public String getDetail() {
      return failedDocumentDetail.getDetail();
    }

    @Override
    public String toString() {
      return "FailedDocumentDetailImpl{failedDocumentDetail=" + failedDocumentDetail + "}";
    }
  }
}
