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
package io.camunda.process.test.impl.judge.jev;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

/**
 * Calls the Jev API over HTTP. Uses httpclient5 (already a dependency of this module) rather than
 * {@code java.net.http.HttpClient}, which is unavailable on this module's Java 8 source/target.
 */
final class Httpclient5JevTransport implements JevTransport {

  private final String baseUrl;
  private final String apiKey;
  private final CloseableHttpClient httpClient;

  Httpclient5JevTransport(final String baseUrl, final String apiKey) {
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    httpClient = HttpClients.createDefault();
  }

  @Override
  public JevHttpResponse post(final String requestBody) {
    final HttpPost request = new HttpPost(baseUrl);
    request.setHeader("Authorization", "Bearer " + apiKey);
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    try {
      return httpClient.execute(
          request,
          response -> {
            final String body =
                response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";
            return new JevHttpResponse(response.getCode(), body);
          });
    } catch (final Exception e) {
      throw new JevRequestException("Failed to call the Jev API at " + baseUrl, e);
    }
  }
}
