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
package io.camunda.zeebe.client.api.response;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.response.DocumentMetadata}
 */
@Deprecated
public interface DocumentMetadata {

  /**
   * @return the content type of the document, if present in the metadata
   */
  String getContentType();

  /**
   * @return the document expiration date, if present in the metadata
   */
  OffsetDateTime getExpiresAt();

  /**
   * @return the document size, if present in the metadata
   */
  Long getSize();

  /**
   * @return the file name of the document, if present in the metadata
   */
  String getFileName();

  /**
   * @return the process definition ID, if present in the metadata
   */
  String getProcessDefinitionId();

  /**
   * @return the process definition key, if present in the metadata
   */
  Long getProcessInstanceKey();

  /**
   * @return the custom properties of the document, if present in the metadata
   */
  Map<String, Object> getCustomProperties();
}
