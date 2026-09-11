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
package io.camunda.client.role;

import static io.camunda.client.impl.http.HttpClientFactory.REST_API_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.filter.ClientFilter;
import io.camunda.client.api.search.filter.RoleFilterBase;
import io.camunda.client.api.search.sort.ClientSort;
import io.camunda.client.api.search.sort.RoleSort;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.protocol.rest.RoleFilter;
import io.camunda.client.protocol.rest.RoleResult;
import io.camunda.client.protocol.rest.RoleSearchQueryRequest;
import io.camunda.client.protocol.rest.RoleSearchQueryResult;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

public class SearchRoleTest extends ClientRestTest {

  public static final String ROLE_ID = "roleId";

  @Test
  public void shouldSearchRoleByRoleId() {
    // given
    gatewayService.onRoleRequest(ROLE_ID, Instancio.create(RoleResult.class));

    // when
    client.newRoleGetRequest(ROLE_ID).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/roles/" + ROLE_ID);
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
  }

  @Test
  public void shouldSearchClientsByRoleId() {
    // when
    client
        .newClientsByRoleSearchRequest(ROLE_ID)
        .sort(ClientSort::clientId)
        .page(fn -> fn.limit(5))
        .send()
        .join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/roles/" + ROLE_ID + "/clients/search");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  void shouldRaiseExceptionOnNullRoleIdWhenSearchingClientsByRoleId() {
    // when / then
    assertThatThrownBy(() -> client.newClientsByRoleSearchRequest(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyRoleIdWhenSearchingClientsByRoleId() {
    // when / then
    assertThatThrownBy(() -> client.newClientsByRoleSearchRequest("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be empty");
  }

  @Test
  void shouldRaiseExceptionWhenFilteringFunctionIsPresentWhenSearchingClientsByRole() {
    assertThatThrownBy(
            () -> client.newClientsByRoleSearchRequest(ROLE_ID).filter(fn -> {}).send().join())
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("This command does not support filtering");
  }

  @Test
  void shouldRaiseExceptionWhenFilteringIsPresentWhenSearchingClientsByRole() {
    assertThatThrownBy(
            () ->
                client
                    .newClientsByRoleSearchRequest(ROLE_ID)
                    .filter(new ClientFilter() {})
                    .send()
                    .join())
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("This command does not support filtering");
  }

  @Test
  public void shouldSearchRoles() {
    // when
    client
        .newRolesSearchRequest()
        .filter(fn -> fn.name("roleName"))
        .sort(RoleSort::name)
        .page(fn -> fn.limit(5))
        .send()
        .join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/roles/search");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  void shouldRaiseExceptionOnRequestError() {
    // given
    gatewayService.errorOnRequest(
        REST_API_PATH + "/roles/" + ROLE_ID,
        () -> new ProblemDetail().title("Not Found").status(404));

    // when / then
    assertThatThrownBy(() -> client.newRoleGetRequest(ROLE_ID).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  @Test
  void shouldRaiseExceptionOnNullRoleId() {
    // when / then
    assertThatThrownBy(() -> client.newRoleGetRequest(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be null");
  }

  @Test
  void shouldSearchRolesWithOrFilters() {
    // when
    client
        .newRolesSearchRequest()
        .filter(
            fn ->
                fn.name("Admin")
                    .orFilters(Arrays.asList(f1 -> f1.roleId("role-1"), f2 -> f2.roleId("role-2"))))
        .send()
        .join();

    // then
    final RoleSearchQueryRequest request =
        gatewayService.getLastRequest(RoleSearchQueryRequest.class);
    final RoleFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getName().get$Eq()).isEqualTo("Admin");
    assertThat(filter.get$Or()).hasSize(2);
    assertThat(filter.get$Or().get(0).getRoleId().get$Eq()).isEqualTo("role-1");
    assertThat(filter.get$Or().get(1).getRoleId().get$Eq()).isEqualTo("role-2");
  }

  @Test
  void shouldSearchRolesByRoleIdLike() {
    // when
    client.newRolesSearchRequest().filter(f -> f.roleId(b -> b.like("role*"))).send().join();

    // then
    final RoleSearchQueryRequest request =
        gatewayService.getLastRequest(RoleSearchQueryRequest.class);
    final RoleFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getRoleId().get$Like()).isEqualTo("role*");
  }

  @Test
  void shouldFallBackToPlainRoleIdFilterOnLegacyClusterRejection() {
    // given -- an 8.9-or-earlier cluster rejects the advanced { "$eq": ... } shape for roleId
    gatewayService.errorThenSuccessOnPostRequest(
        REST_API_PATH + "/roles/search",
        new ProblemDetail()
            .title("INVALID_ARGUMENT")
            .status(400)
            .detail("Request property [filter.roleId] cannot be parsed"),
        Instancio.create(RoleSearchQueryResult.class));

    // when -- the first search retries once and falls back to the plain-string shape
    client.newRolesSearchRequest().filter(fn -> fn.roleId("role-1")).send().join();

    // then
    final List<LoggedRequest> requests = RestGatewayService.getAllRequests();
    assertThat(requests).hasSize(2);
    assertThat(requests)
        .anySatisfy(r -> assertThat(r.getBodyAsString()).contains("\"$eq\":\"role-1\""));
    assertThat(requests)
        .anySatisfy(
            r ->
                assertThat(r.getBodyAsString())
                    .contains("\"roleId\":\"role-1\"")
                    .doesNotContain("$eq"));

    // when -- a second search on the same client goes straight to the remembered plain-string
    // shape, without repeating the doomed first attempt
    client.newRolesSearchRequest().filter(fn -> fn.roleId("role-2")).send().join();

    // then
    final List<LoggedRequest> allRequests = RestGatewayService.getAllRequests();
    assertThat(allRequests).hasSize(3);
    final List<LoggedRequest> role2Requests =
        allRequests.stream()
            .filter(r -> r.getBodyAsString().contains("role-2"))
            .collect(Collectors.toList());
    assertThat(role2Requests).hasSize(1);
    assertThat(role2Requests.get(0).getBodyAsString())
        .contains("\"roleId\":\"role-2\"")
        .doesNotContain("$eq");
  }

  @Test
  void shouldNotFallBackForAdvancedRoleIdFilterOnLegacyClusterRejection() {
    // given -- an advanced operator has no plain-string equivalent, so no fallback is attempted
    gatewayService.errorOnRequest(
        REST_API_PATH + "/roles/search",
        () ->
            new ProblemDetail()
                .title("INVALID_ARGUMENT")
                .status(400)
                .detail("Request property [filter.roleId] cannot be parsed"));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newRolesSearchRequest()
                    .filter(fn -> fn.roleId(b -> b.like("role*")))
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class);
    assertThat(RestGatewayService.getAllRequests()).hasSize(1);
  }

  @Test
  void shouldHaveMatchingFilterMethodsInBaseAndFullInterfaces() {
    final Set<String> baseMethods = publicMethodSignatures(RoleFilterBase.class);
    final Set<String> fullMethods =
        publicMethodSignatures(io.camunda.client.api.search.filter.RoleFilter.class);

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
