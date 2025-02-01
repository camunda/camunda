/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface DocumentReferenceResponse {

  @JsonProperty("camunda.document.type")
  default String getDocumentType() {
    return "camunda";
  }

  /**
   * The ID of the document. In combination with the store ID, the document ID uniquely identifies a
   * document.
   *
   * @return the ID of the document
   */
  String getDocumentId();

  /**
   * The ID of the document store where the document is located. Document IDs are unique within a
   * document store.
   *
   * @return the ID of the document store
   */
  String getStoreId();

  /**
   * The hash of the associated document
   *
   * @return the hash value of the document
   */
  String getContentHash();

  /**
   * @return the metadata of the document reference
   */
  DocumentMetadata getMetadata();
}
