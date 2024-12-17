/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.impl.response;

import io.camunda.zeebe.client.api.response.DocumentReferenceBatchResponse;
import io.camunda.zeebe.client.api.response.DocumentReferenceResponse;
import io.camunda.zeebe.client.protocol.rest.DocumentCreationBatchResponse;
import io.camunda.zeebe.client.protocol.rest.DocumentCreationFailureDetail;
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
    public String getFilename() {
      return failedDocumentDetail.getFilename();
    }

    @Override
    public String getDetail() {
      return failedDocumentDetail.getDetail();
    }
  }
}
