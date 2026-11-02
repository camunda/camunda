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
package io.camunda.process.test.impl.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.command.ProblemException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Verifies that the clock client controls the clock through the public clock API. */
public class CamundaClockClientImplTest {

  private static final String CLOCK_PATH = "/v2/clock";
  private static final String CLOCK_ADD_PATH = "/v2/clock/add";
  private static final String CLOCK_RESET_PATH = "/v2/clock/reset";

  @RegisterExtension
  private static final WireMockExtension REST_API =
      WireMockExtension.newInstance()
          .options(WireMockConfiguration.wireMockConfig().dynamicPort())
          .build();

  private CamundaClockClientImpl clockClient;

  @BeforeEach
  void createClient() {
    clockClient =
        new CamundaClockClientImpl(
            () -> CamundaClient.newClientBuilder().restAddress(URI.create(REST_API.baseUrl())));
  }

  @AfterEach
  void closeClient() {
    clockClient.close();
  }

  @Test
  void shouldGetCurrentTime() {
    // given
    stubCurrentTime("2026-03-01T10:15:30.123Z");

    // when
    final Instant currentTime = clockClient.getCurrentTime();

    // then
    assertThat(currentTime).isEqualTo(Instant.parse("2026-03-01T10:15:30.123Z"));
  }

  @Test
  void shouldIncreaseTime() {
    // given
    stubAddTime();

    // when
    clockClient.increaseTime(Duration.ofDays(2));

    // then
    REST_API.verify(
        postRequestedFor(urlPathEqualTo(CLOCK_ADD_PATH))
            .withRequestBody(equalToJson("{\"offset\": \"PT48H\"}")));
  }

  @Test
  void shouldSetTimeByAddingTheDifferenceToTheCurrentTime() {
    // given
    stubCurrentTime("2026-03-01T10:00:00Z");
    stubAddTime();

    // when
    clockClient.setTime(Instant.parse("2026-03-02T12:30:00Z"));

    // then
    REST_API.verify(
        postRequestedFor(urlPathEqualTo(CLOCK_ADD_PATH))
            .withRequestBody(equalToJson("{\"offset\": \"PT26H30M\"}")));
  }

  @Test
  void shouldSetTimeBeforeTheCurrentTime() {
    // given
    stubCurrentTime("2026-03-02T12:30:00Z");
    stubAddTime();

    // when
    clockClient.setTime(Instant.parse("2026-03-01T10:00:00Z"));

    // then
    REST_API.verify(
        postRequestedFor(urlPathEqualTo(CLOCK_ADD_PATH))
            .withRequestBody(equalToJson("{\"offset\": \"PT-26H-30M\"}")));
  }

  @Test
  void shouldResetTime() {
    // given
    REST_API.stubFor(post(urlPathEqualTo(CLOCK_RESET_PATH)).willReturn(noContent()));

    // when
    clockClient.resetTime();

    // then
    REST_API.verify(postRequestedFor(urlPathEqualTo(CLOCK_RESET_PATH)));
  }

  @Test
  void shouldCloseTheCamundaClient() {
    // given
    final CamundaClient camundaClient = mock(CamundaClient.class);
    final CamundaClientBuilder clientBuilder = mock(CamundaClientBuilder.class);
    when(clientBuilder.build()).thenReturn(camundaClient);

    // when
    new CamundaClockClientImpl(() -> clientBuilder).close();

    // then
    verify(camundaClient).close();
  }

  @Test
  void shouldFailWithArithmeticExceptionIfTimeToAddIsTooLarge() {
    // when / then
    assertThatThrownBy(() -> clockClient.increaseTime(Duration.ofSeconds(Long.MAX_VALUE)))
        .isInstanceOf(ArithmeticException.class);

    REST_API.verify(0, postRequestedFor(urlPathEqualTo(CLOCK_ADD_PATH)));
  }

  @Test
  void shouldFailWithArithmeticExceptionIfTimeToSetIsTooFarAway() {
    // given
    stubCurrentTime("2026-03-01T10:00:00Z");

    // when / then
    assertThatThrownBy(() -> clockClient.setTime(Instant.MAX))
        .isInstanceOf(ArithmeticException.class);

    REST_API.verify(0, postRequestedFor(urlPathEqualTo(CLOCK_ADD_PATH)));
  }

  @Test
  void shouldFailIfTheRuntimeRejectsTheTimeToAdd() {
    // given
    final String reason =
        "Expected the new clock time to be at most 9223372036854775807, but adding"
            + " 9223372036854775807 ms to the current time 1772359200000 goes beyond that";
    REST_API.stubFor(
        post(urlPathEqualTo(CLOCK_ADD_PATH)).willReturn(problem(400, "INVALID_ARGUMENT", reason)));

    // when / then
    assertFailsWithProblem(
        () -> clockClient.increaseTime(Duration.ofMillis(Long.MAX_VALUE)),
        "Failed to increase the time.",
        reason);
  }

  @Test
  void shouldFailIfTheRuntimeRejectsTheTimeToSet() {
    // given
    stubCurrentTime("2026-03-01T10:00:00Z");
    final String reason =
        "Expected the new clock time to be not negative, but adding -1772359200001 ms to the"
            + " current time 1772359200000 makes it -1";
    REST_API.stubFor(
        post(urlPathEqualTo(CLOCK_ADD_PATH)).willReturn(problem(400, "INVALID_ARGUMENT", reason)));

    // when / then
    assertFailsWithProblem(
        () -> clockClient.setTime(Instant.ofEpochMilli(-1)),
        "Failed to increase the time.",
        reason);
  }

  @Test
  void shouldFailIfTheTimeCannotBeRead() {
    // given
    REST_API.stubFor(get(urlPathEqualTo(CLOCK_PATH)).willReturn(forbidden("READ")));

    // when / then
    assertFailsWithProblem(
        () -> clockClient.getCurrentTime(),
        "Failed to read the current time.",
        "Insufficient permissions to perform operation 'READ' on resource 'SYSTEM'");
  }

  @Test
  void shouldFailIfTheRuntimeIsUnavailable() {
    // given
    REST_API.stubFor(get(urlPathEqualTo(CLOCK_PATH)).willReturn(aResponse().withStatus(503)));

    // when / then
    assertThatThrownBy(() -> clockClient.getCurrentTime())
        .hasMessage("Failed to read the current time.")
        .hasCauseExactlyInstanceOf(ClientHttpException.class);
  }

  @Test
  void shouldNotSetTimeIfTheTimeCannotBeRead() {
    // given
    REST_API.stubFor(get(urlPathEqualTo(CLOCK_PATH)).willReturn(forbidden("READ")));

    // when / then
    assertThatThrownBy(() -> clockClient.setTime(Instant.parse("2026-03-02T12:30:00Z")))
        .hasMessage("Failed to read the current time.");

    REST_API.verify(0, postRequestedFor(urlPathEqualTo(CLOCK_ADD_PATH)));
  }

  @Test
  void shouldFailIfTheTimeCannotBeIncreased() {
    // given
    REST_API.stubFor(post(urlPathEqualTo(CLOCK_ADD_PATH)).willReturn(forbidden("UPDATE")));

    // when / then
    assertFailsWithProblem(
        () -> clockClient.increaseTime(Duration.ofHours(1)),
        "Failed to increase the time.",
        "Insufficient permissions to perform operation 'UPDATE' on resource 'SYSTEM'");
  }

  @Test
  void shouldFailIfTheTimeCannotBeReset() {
    // given
    REST_API.stubFor(post(urlPathEqualTo(CLOCK_RESET_PATH)).willReturn(forbidden("UPDATE")));

    // when / then
    assertFailsWithProblem(
        () -> clockClient.resetTime(),
        "Failed to reset the time.",
        "Insufficient permissions to perform operation 'UPDATE' on resource 'SYSTEM'");
  }

  private static void assertFailsWithProblem(
      final ThrowingCallable call, final String expectedMessage, final String expectedReason) {
    assertThatThrownBy(call)
        .hasMessage(expectedMessage)
        .cause()
        .asInstanceOf(InstanceOfAssertFactories.type(ProblemException.class))
        .extracting(problem -> problem.details().getDetail())
        .isEqualTo(expectedReason);
  }

  private static void stubCurrentTime(final String timestamp) {
    REST_API.stubFor(
        get(urlPathEqualTo(CLOCK_PATH))
            .willReturn(okJson("{\"timestamp\": \"" + timestamp + "\"}")));
  }

  private static void stubAddTime() {
    REST_API.stubFor(
        post(urlPathEqualTo(CLOCK_ADD_PATH))
            .willReturn(okJson("{\"timestamp\": \"2026-03-03T00:00:00Z\"}")));
  }

  private static ResponseDefinitionBuilder forbidden(final String permission) {
    return problem(
        403,
        "FORBIDDEN",
        "Insufficient permissions to perform operation '" + permission + "' on resource 'SYSTEM'");
  }

  private static ResponseDefinitionBuilder problem(
      final int status, final String title, final String detail) {
    return aResponse()
        .withStatus(status)
        .withHeader("Content-Type", "application/problem+json")
        .withBody(
            "{\"type\": \"about:blank\", \"title\": \""
                + title
                + "\", \"status\": "
                + status
                + ", \"detail\": \""
                + detail
                + "\"}");
  }
}
