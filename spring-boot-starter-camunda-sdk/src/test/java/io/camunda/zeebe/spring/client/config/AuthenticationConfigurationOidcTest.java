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
package io.camunda.zeebe.spring.client.config;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.zeebe.spring.client.configuration.AuthenticationConfiguration;
import io.camunda.zeebe.spring.common.auth.Authentication;
import io.camunda.zeebe.spring.common.auth.Product;
import io.camunda.zeebe.spring.common.auth.selfmanaged.SelfManagedAuthentication;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import wiremock.com.fasterxml.jackson.databind.node.JsonNodeFactory;

@SpringBootTest(
    classes = {AuthenticationConfiguration.class},
    properties = {
      "camunda.client.mode=oidc",
      "camunda.client.auth.client-id=my-client-id",
      "camunda.client.auth.client-secret=my-client-secret",
      "camunda.client.auth.issuer=http://localhost:14683/auth-server"
    })
@WireMockTest(httpPort = 14683)
public class AuthenticationConfigurationOidcTest {
  private static final String ACCESS_TOKEN =
      JWT.create().withExpiresAt(Instant.now().plusSeconds(300)).sign(Algorithm.none());
  @Autowired Authentication authentication;

  @Test
  void shouldBeSelfManaged() {
    assertThat(authentication).isExactlyInstanceOf(SelfManagedAuthentication.class);
  }

  @Test
  void shouldHaveZeebeAuth() {
    final String accessToken = ACCESS_TOKEN;
    stubFor(
        post("/auth-server/protocol/openid-connect/token")
            .willReturn(
                ok().withJsonBody(
                        JsonNodeFactory.instance
                            .objectNode()
                            .put("access_token", accessToken)
                            .put("expires_in", 300))));
    assertThat(authentication.getTokenHeader(Product.ZEEBE))
        .isNotNull()
        .isEqualTo(entry("Authorization", "Bearer " + accessToken));
    verify(
        postRequestedFor(urlEqualTo("/auth-server/protocol/openid-connect/token"))
            .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded")));
  }
}
