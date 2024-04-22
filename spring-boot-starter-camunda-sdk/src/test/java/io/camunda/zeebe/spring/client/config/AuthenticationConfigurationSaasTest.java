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

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.zeebe.spring.client.configuration.AuthenticationConfiguration;
import io.camunda.zeebe.spring.common.auth.Authentication;
import io.camunda.zeebe.spring.common.auth.Product;
import io.camunda.zeebe.spring.common.auth.saas.SaaSAuthentication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import wiremock.com.fasterxml.jackson.databind.node.JsonNodeFactory;

@SpringBootTest(
    classes = {AuthenticationConfiguration.class},
    properties = {
      "camunda.client.mode=saas",
      "camunda.client.cluster-id=12345",
      "camunda.client.region=bru-2",
      "camunda.client.auth.client-id=my-client-id",
      "camunda.client.auth.client-secret=my-client-secret",
      "camunda.client.auth.issuer=http://localhost:14682/auth-server"
    })
@WireMockTest(httpPort = 14682)
public class AuthenticationConfigurationSaasTest {
  private static final String ACCESS_TOKEN =
      "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6IlFVVXdPVFpDUTBVM01qZEVRME0wTkRFelJrUkJORFk0T0RZeE1FRTBSa1pFUlVWRVF6bERNZyJ9.eyJodHRwczovL2FwaS5jbG91ZC5jYW11bmRhLmlvL2NsaWVudC9vcmdJZCI6IjM2YmQ4MDAxLTUyZGItNGIzZS05ZWZiLWViMzBhZTQyMDM3YiIsImh0dHBzOi8vYXBpLmNsb3VkLmNhbXVuZGEuaW8vY2xpZW50L3V1aWQiOiJjNDJlMjY3Yy03YWE2LTQ5ZmItYTgyMi0zYmQ0NjgxOGUzNzUiLCJpc3MiOiJodHRwczovL3dlYmxvZ2luLmNsb3VkLmNhbXVuZGEuaW8vIiwic3ViIjoiNGdGMmdvc2xWOEN6TWUyZ0cwVFB1bTNZeDF5TWRld3FAY2xpZW50cyIsImF1ZCI6ImFwaS5jbG91ZC5jYW11bmRhLmlvIiwiaWF0IjoxNzA5MTIxNTIzLCJleHAiOjE3MDkyMDc5MjMsImF6cCI6IjRnRjJnb3NsVjhDek1lMmdHMFRQdW0zWXgxeU1kZXdxIiwic2NvcGUiOiJHZXRXaGl0ZWxpc3RzIFVwZGF0ZVdoaXRlbGlzdHMiLCJndHkiOiJjbGllbnQtY3JlZGVudGlhbHMifQ.VlPxXPFFK3Oc8qfYKnPNmTKB29AdLnFRIDz0lmRJ2rDKdd-91D90-qM4Br11HW9tka2BYyJZG3BGkLugV_W0RgycrjGG4kWMAlI22axtU5h8BGiVHFYmudb8eZSYxDk7SN-872sJ1CSSeYkADuGZ_FoTJFEudunta8dVivtaoNXOvIaY8qS8w0Rmyd5plA6oia96pynJYWnwYELim8AAPNX24zWSZW6N16tPRxXT8YPBhg4a-XHu9gM_Q_8modroCFc8q5xw_aApWpbNxFaffMr2lSB9mK7AqXKBbrDzpL4bhZ7lSZVrF3lWVI0lGoROyQXUBYNvy24i-NHVDU0olA";
  @Autowired Authentication authentication;

  @Test
  void shouldBeSaas() {
    assertThat(authentication).isExactlyInstanceOf(SaaSAuthentication.class);
  }

  @Test
  void shouldHaveZeebeAuth() {
    stubFor(
        post("/auth-server")
            .willReturn(
                ok().withJsonBody(
                        JsonNodeFactory.instance
                            .objectNode()
                            .put("access_token", ACCESS_TOKEN)
                            .put("expires_in", 300))));
    assertThat(authentication.getTokenHeader(Product.ZEEBE))
        .isNotNull()
        .isEqualTo(entry("Authorization", "Bearer " + ACCESS_TOKEN));
    verify(
        postRequestedFor(urlEqualTo("/auth-server"))
            .withHeader("Content-Type", equalTo("application/json")));
  }
}
