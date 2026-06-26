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
package io.camunda.client.api.command;

import io.camunda.client.api.response.DocumentReferenceResponse;
import java.util.Map;

/**
 * A content block in an agent instance history item. Use the static factory methods to create
 * instances; downcast to {@link TextContent}, {@link DocumentContent}, or {@link ObjectContent} to
 * access type-specific fields.
 */
public interface AgentInstanceHistoryContent {

  String getContentType();

  /**
   * Creates a plain-text content block.
   *
   * @param text the text. Must not be null.
   * @return an {@link AgentInstanceHistoryContent} of type TEXT
   */
  static AgentInstanceHistoryContent text(final String text) {
    if (text == null) {
      throw new IllegalArgumentException("text must not be null");
    }
    return new TextContent(text);
  }

  /**
   * Creates an arbitrary-object content block.
   *
   * @param object the key-value map. Must not be null.
   * @return an {@link AgentInstanceHistoryContent} of type OBJECT
   */
  static AgentInstanceHistoryContent object(final Map<String, Object> object) {
    if (object == null) {
      throw new IllegalArgumentException("object must not be null");
    }
    return new ObjectContent(object);
  }

  /**
   * Creates a document-reference content block from a {@link DocumentReferenceResponse} returned by
   * other document APIs (e.g. upload or create-link).
   *
   * @param documentReference the document reference. Must not be null.
   * @return a {@link DocumentContent} of type DOCUMENT
   */
  static DocumentContent document(final DocumentReferenceResponse documentReference) {
    if (documentReference == null) {
      throw new IllegalArgumentException("documentReference must not be null");
    }
    return new DocumentContent(documentReference);
  }

  /** Plain-text content block ({@code contentType = "TEXT"}). */
  final class TextContent implements AgentInstanceHistoryContent {
    private final String text;

    public TextContent(final String text) {
      this.text = text;
    }

    @Override
    public String getContentType() {
      return "TEXT";
    }

    public String getText() {
      return text;
    }
  }

  /** Arbitrary structured content block ({@code contentType = "OBJECT"}). */
  final class ObjectContent implements AgentInstanceHistoryContent {
    private final Map<String, Object> object;

    public ObjectContent(final Map<String, Object> object) {
      this.object = object;
    }

    @Override
    public String getContentType() {
      return "OBJECT";
    }

    public Map<String, Object> getObject() {
      return object;
    }
  }

  /** Camunda Document Store reference content block ({@code contentType = "DOCUMENT"}). */
  final class DocumentContent implements AgentInstanceHistoryContent {
    private final DocumentReferenceResponse documentReference;

    public DocumentContent(final DocumentReferenceResponse documentReference) {
      this.documentReference = documentReference;
    }

    @Override
    public String getContentType() {
      return "DOCUMENT";
    }

    public DocumentReferenceResponse getDocumentReference() {
      return documentReference;
    }
  }
}
