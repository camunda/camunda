/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.api;

import java.io.InputStream;

public interface DocumentStore {

  DocumentOperationResponse<DocumentReference> createDocument(DocumentCreationRequest request);

  DocumentOperationResponse<InputStream> getDocument(String documentId);

  DocumentOperationResponse<Void> deleteDocument(String documentId);

  DocumentOperationResponse<DocumentLink> createLink(String documentId, long durationInSeconds);
}
