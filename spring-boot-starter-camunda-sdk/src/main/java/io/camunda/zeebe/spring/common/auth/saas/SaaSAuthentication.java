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
package io.camunda.zeebe.spring.common.auth.saas;

import io.camunda.zeebe.spring.common.auth.Product;
import io.camunda.zeebe.spring.common.auth.jwt.JwtAuthentication;
import io.camunda.zeebe.spring.common.auth.jwt.JwtConfig;
import io.camunda.zeebe.spring.common.auth.jwt.JwtCredential;
import io.camunda.zeebe.spring.common.json.JsonMapper;
import java.time.LocalDateTime;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SaaSAuthentication extends JwtAuthentication {

  private static final Logger LOG = LoggerFactory.getLogger(SaaSAuthentication.class);

  private final JsonMapper jsonMapper;

  public SaaSAuthentication(final JwtConfig jwtConfig, final JsonMapper jsonMapper) {
    super(jwtConfig);
    this.jsonMapper = jsonMapper;
  }

  public static SaaSAuthenticationBuilder builder() {
    return new SaaSAuthenticationBuilder();
  }

  private TokenResponse retrieveToken(final Product product, final JwtCredential jwtCredential) {
    try (final CloseableHttpClient client = HttpClients.createDefault()) {
      final HttpPost request = buildRequest(jwtCredential);
      return client.execute(
          request,
          response -> {
            try {
              return jsonMapper.fromJson(
                  EntityUtils.toString(response.getEntity()), TokenResponse.class);
            } catch (final Exception e) {
              final var errorMessage =
                  """
              Token retrieval failed from: {}
              Response code: {}
              Audience: {}
              """;
              LOG.error(
                  errorMessage,
                  jwtCredential.getAuthUrl(),
                  response.getCode(),
                  jwtCredential.getAudience());
              throw e;
            }
          });
    } catch (final Exception e) {
      LOG.error("Authenticating for " + product + " failed due to " + e);
      throw new RuntimeException("Unable to authenticate", e);
    }
  }

  private HttpPost buildRequest(final JwtCredential jwtCredential) {
    final HttpPost httpPost = new HttpPost(jwtCredential.getAuthUrl());
    httpPost.addHeader("Content-Type", "application/json");
    final TokenRequest tokenRequest =
        new TokenRequest(
            jwtCredential.getAudience(),
            jwtCredential.getClientId(),
            jwtCredential.getClientSecret());
    httpPost.setEntity(new StringEntity(jsonMapper.toJson(tokenRequest)));
    return httpPost;
  }

  @Override
  protected JwtToken generateToken(final Product product, final JwtCredential credential) {
    final TokenResponse tokenResponse = retrieveToken(product, credential);
    return new JwtToken(
        tokenResponse.getAccessToken(),
        LocalDateTime.now().plusSeconds(tokenResponse.getExpiresIn()));
  }
}
