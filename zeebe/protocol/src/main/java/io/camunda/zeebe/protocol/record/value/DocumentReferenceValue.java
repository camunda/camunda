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
package io.camunda.zeebe.protocol.record.value;

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import java.util.Map;
import org.immutables.value.Value;

/**
 * Represents a reference to a Camunda document, mirroring the {@code DocumentReference} schema in
 * {@code documents.yaml}. This top-level value type can be reused across record types that need to
 * reference a stored document.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableDocumentReferenceValue.Builder.class)
public interface DocumentReferenceValue {

  /** Returns the unique identifier of the document. */
  String getDocumentId();

  /** Returns the identifier of the store where the document is held. */
  String getStoreId();

  /**
   * Returns the content hash of the document.
   *
   * <p>Required to construct the document download URL via the Camunda Document Store REST API
   * ({@code GET /v2/documents/{documentId}}). Without this hash the document cannot be retrieved
   * from the record alone.
   */
  String getContentHash();

  /** Returns the metadata associated with this document reference. */
  DocumentReferenceMetadataValue getMetadata();

  /**
   * Represents the metadata of a document, mirroring the {@code DocumentMetadataResponse} schema in
   * {@code documents.yaml}.
   */
  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableDocumentReferenceMetadataValue.Builder.class)
  interface DocumentReferenceMetadataValue {

    /** Returns the MIME content type of the document (e.g. {@code application/pdf}). */
    String getContentType();

    /** Returns the file name of the document. */
    String getFileName();

    /**
     * Returns the epoch-millis timestamp at which this document expires, or {@code -1} if the
     * document does not expire.
     */
    long getExpiresAt();

    /** Returns the size of the document in bytes, or {@code -1} if unknown. */
    long getSize();

    /**
     * Returns the ID of the process definition that created this document, or an empty string if
     * not set.
     */
    String getProcessDefinitionId();

    /**
     * Returns the key of the process instance that created this document, or {@code -1} if not set.
     */
    long getProcessInstanceKey();

    /** Returns the custom properties associated with this document. */
    Map<String, Object> getCustomProperties();
  }
}
