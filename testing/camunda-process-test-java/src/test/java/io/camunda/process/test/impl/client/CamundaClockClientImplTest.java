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

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
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
  void shouldResetTime() {
    // given
    REST_API.stubFor(post(urlPathEqualTo(CLOCK_RESET_PATH)).willReturn(noContent()));

    // when
    clockClient.resetTime();

    // then
    REST_API.verify(postRequestedFor(urlPathEqualTo(CLOCK_RESET_PATH)));
  }

  @Test
  void shouldFailWithArithmeticExceptionIfTimeToAddIsTooLarge() {
    // when / then
    assertThatThrownBy(() -> clockClient.increaseTime(Duration.ofSeconds(Long.MAX_VALUE)))
        .isInstanceOf(ArithmeticException.class);

    REST_API.verify(0, postRequestedFor(urlPathEqualTo(CLOCK_ADD_PATH)));
  }

  @Test
  void shouldFailIfTheTimeCannotBeRead() {
    // given
    REST_API.stubFor(get(urlPathEqualTo(CLOCK_PATH)).willReturn(forbidden()));

    // when / then
    assertThatThrownBy(() -> clockClient.getCurrentTime())
        .hasMessage("Failed to read the current time.")
        .hasCauseInstanceOf(ProblemException.class);
  }

  @Test
  void shouldFailIfTheTimeCannotBeIncreased() {
    // given
    REST_API.stubFor(post(urlPathEqualTo(CLOCK_ADD_PATH)).willReturn(forbidden()));

    // when / then
    assertThatThrownBy(() -> clockClient.increaseTime(Duration.ofHours(1)))
        .hasMessage("Failed to increase the time.")
        .hasCauseInstanceOf(ProblemException.class);
  }

  @Test
  void shouldFailIfTheTimeCannotBeReset() {
    // given
    REST_API.stubFor(post(urlPathEqualTo(CLOCK_RESET_PATH)).willReturn(forbidden()));

    // when / then
    assertThatThrownBy(() -> clockClient.resetTime())
        .hasMessage("Failed to reset the time.")
        .hasCauseInstanceOf(ProblemException.class);
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

  private static ResponseDefinitionBuilder forbidden() {
    return aResponse()
        .withStatus(403)
        .withHeader("Content-Type", "application/problem+json")
        .withBody(
            "{\"type\": \"about:blank\", \"title\": \"FORBIDDEN\", \"status\": 403,"
                + " \"detail\": \"Insufficient permissions\"}");
  }
}
