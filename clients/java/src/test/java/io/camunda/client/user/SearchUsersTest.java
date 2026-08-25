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
package io.camunda.client.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.search.filter.UserFilterBase;
import io.camunda.client.api.search.sort.UserSort;
import io.camunda.client.protocol.rest.UserFilter;
import io.camunda.client.protocol.rest.UserResult;
import io.camunda.client.protocol.rest.UserSearchQueryRequest;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

public class SearchUsersTest extends ClientRestTest {

  @Test
  public void shouldSearchUsers() {
    // when
    client
        .newUsersSearchRequest()
        .filter(fn -> fn.username("userName"))
        .sort(UserSort::username)
        .page(fn -> fn.limit(5))
        .send()
        .join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/users/search");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  public void shouldSearchUserByUsername() {
    // given
    final String username = "username";
    gatewayService.onUserRequest(username, Instancio.create(UserResult.class));

    // when
    client.newUserGetRequest(username).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getUserUrl(username));
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
  }

  @Test
  void shouldRaiseExceptionOnNullUsername() {
    // when / then
    assertThatThrownBy(() -> client.newUserGetRequest(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("username must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyUsername() {
    // when / then
    assertThatThrownBy(() -> client.newUserGetRequest("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("username must not be empty");
  }

  @Test
  void shouldSearchUsersWithOrFilters() {
    // when
    client
        .newUsersSearchRequest()
        .filter(
            fn ->
                fn.orFilters(
                    Arrays.asList(f1 -> f1.username("user-1"), f2 -> f2.username("user-2"))))
        .send()
        .join();

    // then
    final UserSearchQueryRequest request =
        gatewayService.getLastRequest(UserSearchQueryRequest.class);
    final UserFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.get$Or()).hasSize(2);
    assertThat(filter.get$Or().get(0).getUsername().get$Eq()).isEqualTo("user-1");
    assertThat(filter.get$Or().get(1).getUsername().get$Eq()).isEqualTo("user-2");
  }

  @Test
  void shouldHaveMatchingFilterMethodsInBaseAndFullInterfaces() {
    final Set<String> baseMethods = publicMethodSignatures(UserFilterBase.class);
    final Set<String> fullMethods =
        publicMethodSignatures(io.camunda.client.api.search.filter.UserFilter.class);

    assertThat(fullMethods)
        .withFailMessage("Full interface is missing methods from base interface")
        .containsAll(baseMethods);

    final Set<String> expectedExtras = new HashSet<>();
    expectedExtras.add("orFilters[interface java.util.List]");
    final Set<String> actualExtras = new HashSet<>(fullMethods);
    actualExtras.removeAll(baseMethods);

    assertThat(actualExtras)
        .withFailMessage("Unexpected methods in full interface: %s", actualExtras)
        .isEqualTo(expectedExtras);
  }

  private static Set<String> publicMethodSignatures(final Class<?> clazz) {
    return Arrays.stream(clazz.getMethods())
        .filter(m -> Modifier.isPublic(m.getModifiers()) && !m.isSynthetic())
        .map(m -> m.getName() + Arrays.toString(m.getParameterTypes()))
        .collect(Collectors.toSet());
  }
}
