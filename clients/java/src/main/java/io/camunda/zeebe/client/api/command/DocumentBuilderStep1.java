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

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.command.DocumentBuilderStep1}
 */
@Deprecated
public interface DocumentBuilderStep1 {

  /**
   * Sets the content of the document.
   *
   * @param content the content of the document as stream
   */
  DocumentBuilderStep2 content(InputStream content);

  /**
   * Sets the content of the document.
   *
   * @param content the content of the document as byte array
   */
  DocumentBuilderStep2 content(byte[] content);

  /**
   * Sets the content of the document.
   *
   * @param content the content of the document as string
   */
  DocumentBuilderStep2 content(String content);

  interface DocumentBuilderStep2 {

    /**
     * Sets the content type of the document.
     *
     * @param contentType the content type of the document
     */
    DocumentBuilderStep2 contentType(String contentType);

    /**
     * Sets the file name of the document.
     *
     * @param name the file name of the document
     */
    DocumentBuilderStep2 fileName(String name);

    /**
     * Sets the time-to-live of the document. The document will be automatically deleted after the
     * time-to-live is exceeded.
     *
     * <p>Depending on the document store, a maximum and a default time-to-live strategy may be
     * enforced.
     *
     * @param timeToLive the time-to-live of the document
     */
    DocumentBuilderStep2 timeToLive(Duration timeToLive);

    /**
     * Adds a custom key-value pair to the document metadata.
     *
     * @param key custom metadata key
     * @param value custom metadata value
     */
    DocumentBuilderStep2 customMetadata(String key, Object value);

    /**
     * Adds custom key-value pairs to the document metadata.
     *
     * @param customMetadata custom metadata key-value pairs
     */
    DocumentBuilderStep2 customMetadata(Map<String, Object> customMetadata);
  }
}
