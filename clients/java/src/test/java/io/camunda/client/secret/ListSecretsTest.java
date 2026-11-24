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
package io.camunda.client.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.ListSecretsResponse;
import io.camunda.client.api.search.enums.SecretStatus;
import io.camunda.client.api.search.filter.SecretListFilter;
import io.camunda.client.api.search.filter.builder.SecretStatusProperty;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.protocol.rest.SecretListResult;
import io.camunda.client.protocol.rest.SecretStatusEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import io.camunda.client.util.RestGatewayService;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

public class ListSecretsTest extends ClientRestTest {

  private static final String REFERENCE = "camunda.secrets.token";
  private static final String OTHER_REFERENCE = "camunda.secrets.a";

  @Test
  void shouldPostToListEndpoint() {
    // given
    gatewayService.onSecretsListRequest(new SecretListResult());

    // when
    client.newListSecretsCommand().send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getSecretsListUrl());
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  void shouldSendEmptyJsonObjectAsBody() {
    // given
    gatewayService.onSecretsListRequest(new SecretListResult());

    // when
    client.newListSecretsCommand().send().join();

    // then
    assertThat(RestGatewayService.getLastRequest().getBodyAsString()).isEqualTo("{}");
  }

  @ParameterizedTest
  @EnumSource(SecretStatus.class)
  void shouldSendStatusFilter(final SecretStatus status) {
    // given
    gatewayService.onSecretsListRequest(new SecretListResult());

    // when
    client.newListSecretsCommand().filter(f -> f.status(status)).send().join();

    // then
    assertThat(RestGatewayService.getLastRequest().getBodyAsString())
        .isEqualTo(
            "{\"filter\":{\"status\":{\"$eq\":\""
                + status.name()
                + "\",\"$in\":[],\"$notIn\":[]}}}");
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("advancedStatusFilters")
  void shouldSendAdvancedStatusFilter(
      final Consumer<SecretStatusProperty> fn, final String expectedStatus) {
    // given
    gatewayService.onSecretsListRequest(new SecretListResult());

    // when
    client.newListSecretsCommand().filter(f -> f.status(fn)).send().join();

    // then
    assertThat(RestGatewayService.getLastRequest().getBodyAsString())
        .isEqualTo("{\"filter\":{\"status\":" + expectedStatus + "}}");
  }

  /**
   * Drives every status the generated protocol enum knows, excluding only the generator's unknown
   * default. Deliberately not an explicit name list: a status the API contract gains is picked up
   * by the enum source, and {@code SecretStatus.valueOf(status.name())} then fails until {@link
   * SecretStatus} mirrors it, so the client cannot silently lag behind the contract.
   */
  @ParameterizedTest
  @EnumSource(
      value = SecretStatusEnum.class,
      mode = EnumSource.Mode.EXCLUDE,
      names = {"UNKNOWN_DEFAULT_OPEN_API"})
  void shouldOfferEveryProtocolStatus(final SecretStatusEnum status) {
    // given
    gatewayService.onSecretsListRequest(new SecretListResult());

    // when
    client
        .newListSecretsCommand()
        .filter(f -> f.status(SecretStatus.valueOf(status.name())))
        .send()
        .join();

    // then
    assertThat(RestGatewayService.getLastRequest().getBodyAsString())
        .isEqualTo(
            "{\"filter\":{\"status\":{\"$eq\":\""
                + status.getValue()
                + "\",\"$in\":[],\"$notIn\":[]}}}");
  }

  @ParameterizedTest
  @MethodSource("filtersWithoutCriteria")
  void shouldOmitFilterWithoutCriteria(final Consumer<SecretListFilter> fn) {
    // given
    gatewayService.onSecretsListRequest(new SecretListResult());

    // when
    client.newListSecretsCommand().filter(fn).send().join();

    // then the body is the one every cluster accepts, also those before 8.11
    assertThat(RestGatewayService.getLastRequest().getBodyAsString()).isEqualTo("{}");
  }

  @Test
  void shouldReplaceFilterOfPreviousCall() {
    // given
    gatewayService.onSecretsListRequest(new SecretListResult());

    // when
    client
        .newListSecretsCommand()
        .filter(f -> f.status(SecretStatus.EXPIRED))
        .filter(f -> f.status(SecretStatus.DISABLED))
        .send()
        .join();

    // then
    assertThat(RestGatewayService.getLastRequest().getBodyAsString())
        .isEqualTo("{\"filter\":{\"status\":{\"$eq\":\"DISABLED\",\"$in\":[],\"$notIn\":[]}}}");
  }

  @Test
  void shouldReturnReferencesOfFilteredList() {
    // given
    gatewayService.onSecretsListRequest(new SecretListResult().addReferencesItem(REFERENCE));

    // when
    final ListSecretsResponse response =
        client.newListSecretsCommand().filter(f -> f.status(SecretStatus.EXPIRED)).send().join();

    // then
    assertThat(response.getReferences()).containsExactly(REFERENCE);
  }

  @Test
  void shouldRejectNullFilterConsumer() {
    // when / then
    assertThatThrownBy(() -> client.newListSecretsCommand().filter(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fn");
  }

  @Test
  void shouldSendJsonContentType() {
    // given the endpoint only accepts JSON, so a body-less request would be rejected
    gatewayService.onSecretsListRequest(new SecretListResult());

    // when
    client.newListSecretsCommand().send().join();

    // then
    assertThat(RestGatewayService.getLastRequest().getHeader("Content-Type"))
        .contains("application/json");
  }

  @Test
  void shouldReturnReferences() {
    // given
    gatewayService.onSecretsListRequest(
        new SecretListResult().addReferencesItem(REFERENCE).addReferencesItem(OTHER_REFERENCE));

    // when
    final ListSecretsResponse response = client.newListSecretsCommand().send().join();

    // then
    assertThat(response.getReferences()).containsExactly(REFERENCE, OTHER_REFERENCE);
  }

  @Test
  void shouldReturnUnmodifiableReferences() {
    // given
    gatewayService.onSecretsListRequest(new SecretListResult().addReferencesItem(REFERENCE));

    // when
    final ListSecretsResponse response = client.newListSecretsCommand().send().join();

    // then the response cannot be altered through the list it hands out
    assertThat(response.getReferences()).isUnmodifiable();
  }

  @Test
  void shouldReturnEmptyListWhenResponseOmitsReferences() {
    // given
    gatewayService.onSecretsListRequest(new SecretListResult());

    // when
    final ListSecretsResponse response = client.newListSecretsCommand().send().join();

    // then
    assertThat(response.getReferences()).isEmpty();
  }

  @Test
  void shouldRaiseExceptionOnRejectedRequest() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getSecretsListUrl(),
        () -> new ProblemDetail().title("Invalid data").status(400));

    // when / then
    assertThatThrownBy(() -> client.newListSecretsCommand().send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 400")
        .hasMessageContaining("Invalid data");
  }

  @Test
  void shouldRaiseExceptionWhenUnauthenticated() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getSecretsListUrl(),
        () -> new ProblemDetail().title("Unauthorized").status(401));

    // when / then
    assertThatThrownBy(() -> client.newListSecretsCommand().send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 401: 'Unauthorized'");
  }

  @Test
  void shouldRaiseExceptionOnServerError() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getSecretsListUrl(),
        () -> new ProblemDetail().title("Internal Server Error").status(500));

    // when / then
    assertThatThrownBy(() -> client.newListSecretsCommand().send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 500: 'Internal Server Error'");
  }

  private static Stream<Arguments> advancedStatusFilters() {
    return Stream.of(
        statusFilter(
            s -> s.neq(SecretStatus.ACTIVE), "{\"$neq\":\"ACTIVE\",\"$in\":[],\"$notIn\":[]}"),
        statusFilter(
            s -> s.in(SecretStatus.DISABLED, SecretStatus.EXPIRED),
            "{\"$in\":[\"DISABLED\",\"EXPIRED\"],\"$notIn\":[]}"),
        statusFilter(
            s -> s.notIn(Collections.singletonList(SecretStatus.ACTIVE)),
            "{\"$in\":[],\"$notIn\":[\"ACTIVE\"]}"),
        statusFilter(s -> s.exists(true), "{\"$exists\":true,\"$in\":[],\"$notIn\":[]}"),
        statusFilter(s -> s.like("*ACTIVE"), "{\"$in\":[],\"$notIn\":[],\"$like\":\"*ACTIVE\"}"));
  }

  private static Arguments statusFilter(
      final Consumer<SecretStatusProperty> fn, final String expectedStatus) {
    return Arguments.of(fn, expectedStatus);
  }

  private static Stream<Consumer<SecretListFilter>> filtersWithoutCriteria() {
    return Stream.of(f -> f.status((SecretStatus) null), f -> f.status(s -> {}));
  }
}
