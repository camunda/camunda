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
package io.camunda.zeebe.client.impl.util;

import static io.camunda.zeebe.client.impl.command.ArgumentUtil.ensureNotNull;

import io.camunda.zeebe.client.api.command.DocumentBuilderStep1;
import io.camunda.zeebe.client.api.command.DocumentBuilderStep1.DocumentBuilderStep2;
import io.camunda.zeebe.client.protocol.rest.DocumentDetails;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

public class DocumentBuilder implements DocumentBuilderStep1, DocumentBuilderStep2 {

  private InputStream content = null;
  private final DocumentDetails metadata = new DocumentDetails();

  @Override
  public DocumentBuilderStep2 content(final InputStream content) {
    ensureNotNull("content", content);
    this.content = content;
    return this;
  }

  @Override
  public DocumentBuilderStep2 content(final byte[] content) {
    ensureNotNull("content", content);
    this.content = new ByteArrayInputStream(content);
    return this;
  }

  @Override
  public DocumentBuilderStep2 content(final String content) {
    ensureNotNull("content", content);
    this.content = new ByteArrayInputStream(content.getBytes());
    return this;
  }

  @Override
  public DocumentBuilderStep2 contentType(final String contentType) {
    metadata.setContentType(contentType);
    return this;
  }

  @Override
  public DocumentBuilderStep2 fileName(final String name) {
    metadata.setFileName(name);
    return this;
  }

  @Override
  public DocumentBuilderStep2 timeToLive(final Duration timeToLive) {
    final OffsetDateTime expiresAt = OffsetDateTime.now().plus(timeToLive);
    metadata.setExpiresAt(expiresAt.toString());
    return this;
  }

  @Override
  public DocumentBuilderStep2 customMetadata(final String key, final Object value) {
    ensureNotNull("key", key);
    ensureNotNull("value", value);
    if (metadata.getCustomProperties() == null) {
      metadata.setCustomProperties(new HashMap<>());
    }
    metadata.getCustomProperties().put(key, value);
    return this;
  }

  @Override
  public DocumentBuilderStep2 customMetadata(final Map<String, Object> customMetadata) {
    ensureNotNull("customMetadata", customMetadata);
    if (metadata.getCustomProperties() == null) {
      metadata.setCustomProperties(new HashMap<>());
    }
    metadata.getCustomProperties().putAll(customMetadata);
    return this;
  }

  public DocumentDetails getMetadata() {
    return metadata;
  }

  public InputStream getContent() {
    ensureNotNull("content", content);
    return content;
  }
}
