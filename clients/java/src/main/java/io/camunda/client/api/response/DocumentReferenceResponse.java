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
package io.camunda.client.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.client.api.ExperimentalApi;

@ExperimentalApi("https://github.com/camunda/issues/issues/841")
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
   * @return the metadata of the document reference
   */
  DocumentMetadata getMetadata();
}
