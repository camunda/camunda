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
package io.camunda.client.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.protocol.rest.AuthorizationResult;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

public class SearchAuthorizationTest extends ClientRestTest {

  public static final long AUTHORIZATION_KEY = 100L;

  @Test
  public void shouldSearchAuthorizationByAuthorizationKey() {
    // given
    gatewayService.onAuthorizationRequest(
        AUTHORIZATION_KEY, Instancio.create(AuthorizationResult.class).authorizationKey("1"));

    // when
    client.newAuthorizationGetRequest(AUTHORIZATION_KEY).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getAuthorizationUrl(AUTHORIZATION_KEY));
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
  }

  @Test
  void shouldRaiseExceptionOnNegativeAuthorizationKey() {
    // when / then
    assertThatThrownBy(() -> client.newAuthorizationGetRequest(-1).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("authorizationKey must be greater than 0");
  }

  @Test
  public void shouldSearchAuthorizations() {
    // when
    client
        .newAuthorizationSearchRequest()
        .filter(fn -> fn.ownerId("ownerId"))
        .sort(s -> s.ownerType().desc())
        .page(fn -> fn.limit(5))
        .send()
        .join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/authorizations/search");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  void shouldIncludeSortAndFilterInAuthorizationsSearchRequestBody() {
    // when
    client
        .newAuthorizationSearchRequest()
        .filter(fn -> fn.ownerId("ownerId"))
        .sort(s -> s.ownerType().desc())
        .page(fn -> fn.limit(5))
        .send()
        .join();

    // then
    final LoggedRequest lastRequest = gatewayService.getLastRequest();
    final String requestBody = lastRequest.getBodyAsString();

    assertThat(requestBody).contains("\"sort\":[{\"field\":\"ownerType\",\"order\":\"DESC\"}]");
    assertThat(requestBody).contains("\"filter\":{\"ownerId\"");
  }
}
