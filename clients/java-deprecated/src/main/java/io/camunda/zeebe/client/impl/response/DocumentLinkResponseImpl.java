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
package io.camunda.zeebe.client.impl.response;

import io.camunda.zeebe.client.api.response.DocumentLinkResponse;
import io.camunda.zeebe.client.protocol.rest.DocumentLink;
import java.time.OffsetDateTime;

/**
 * @deprecated since 8.8 for removal in 8.10, replaced by {@link
 *     io.camunda.client.impl.response.DocumentLinkResponseImpl}. Please see the <a
 *     href="https://docs.camunda.io/docs/8.8/apis-tools/migration-manuals/migrate-to-camunda-java-client/">Camunda
 *     Java Client migration guide</a>
 */
@Deprecated
public class DocumentLinkResponseImpl implements DocumentLinkResponse {

  private final String url;
  private final OffsetDateTime expiresAt;

  public DocumentLinkResponseImpl(final DocumentLink documentLink) {
    url = documentLink.getUrl();
    if (documentLink.getExpiresAt() != null) {
      expiresAt = OffsetDateTime.parse(documentLink.getExpiresAt());
    } else {
      expiresAt = null;
    }
  }

  @Override
  public String getUrl() {
    return url;
  }

  @Override
  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }
}
