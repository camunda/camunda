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

import io.camunda.zeebe.client.api.response.DocumentLinkResponse;
import java.time.Duration;

/**
 * Command to create a document link in the document store.
 *
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.command.CreateDocumentLinkCommandStep1}
 */
@Deprecated
public interface CreateDocumentLinkCommandStep1 extends FinalCommandStep<DocumentLinkResponse> {

  /**
   * Sets the document store ID. If not set, the default document store for the cluster will be
   * used.
   *
   * @param storeId the document store ID
   */
  CreateDocumentLinkCommandStep1 storeId(final String storeId);

  /**
   * Sets the document link TTL. If not set, the default TTL for the document store will be used.
   * The TTL must be a positive duration.
   *
   * @param timeToLive the time to live of the document link
   */
  CreateDocumentLinkCommandStep1 timeToLive(final Duration timeToLive);

  /**
   * Sets the documents content hash.
   *
   * @param contentHash the documents content Hash
   */
  CreateDocumentLinkCommandStep1 contentHash(final String contentHash);
}
