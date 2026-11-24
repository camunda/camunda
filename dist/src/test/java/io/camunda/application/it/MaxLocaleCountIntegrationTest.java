/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.application.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.rest.TomcatMaxLocaleConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Integration test for <a href="https://github.com/camunda/camunda/issues/68071">#68071</a>.
 * Verifies, against a real embedded Tomcat, that a request carrying more {@code Accept-Language}
 * values than Tomcat's default {@code maxLocaleCount} (10) is served instead of failing with a 500.
 *
 * <p>This boots a real embedded Tomcat rather than using MockMvc: the limit is enforced by Tomcat's
 * {@code Request.parseLocales}, which MockMvc bypasses entirely. It also guards the bean wiring —
 * {@link TomcatMaxLocaleConfig} is picked up by component scanning in production, so a unit test
 * that only calls the customizer would not notice if the bean stopped being registered.
 */
class MaxLocaleCountIntegrationTest {

  /** 11 values: one more than Tomcat's default limit. */
  private static final int MORE_THAN_DEFAULT_LIMIT = 11;

  /** A value far above the default limit, to show that no cap is left in place. */
  private static final int MANY_LOCALES = 200;

  private HttpResponse<String> sendGetWithLocales(final int port, final int count)
      throws Exception {
    final var acceptLanguage =
        IntStream.rangeClosed(1, count).mapToObj(i -> "en-x-" + i).collect(Collectors.joining(","));
    try (var client = HttpClient.newHttpClient()) {
      final var request =
          HttpRequest.newBuilder()
              .uri(URI.create("http://localhost:" + port + "/locale-probe"))
              .header("Accept-Language", acceptLanguage)
              .GET()
              .build();
      return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
  }

  @Nested
  @SpringBootTest(
      webEnvironment = WebEnvironment.RANDOM_PORT,
      classes = MaxLocaleCountIntegrationTest.WithConfig.class)
  class WithLocaleLimitRemoved {

    @LocalServerPort private int port;

    @Test
    void shouldServeRequestWithMoreThanTenAcceptLanguageValues() throws Exception {
      // when
      final var response = sendGetWithLocales(port, MORE_THAN_DEFAULT_LIMIT);

      // then — the request reaches the controller instead of failing in parseLocales
      assertThat(response.statusCode())
          .as("a request with 11 Accept-Language values should be served")
          .isEqualTo(200);
      assertThat(response.body()).isEqualTo("ok");
    }

    @Test
    void shouldServeRequestWithFarMoreAcceptLanguageValues() throws Exception {
      // when
      final var response = sendGetWithLocales(port, MANY_LOCALES);

      // then — -1 removes the limit rather than raising it
      assertThat(response.statusCode())
          .as("a request with 200 Accept-Language values should be served")
          .isEqualTo(200);
    }
  }

  @Nested
  @SpringBootTest(
      webEnvironment = WebEnvironment.RANDOM_PORT,
      classes = MaxLocaleCountIntegrationTest.WithoutConfig.class)
  class WithDefaultLocaleLimit {

    @LocalServerPort private int port;

    @Test
    void shouldFailWithFiveHundredByDefault() throws Exception {
      // when — same request, same Tomcat, without TomcatMaxLocaleConfig
      final var response = sendGetWithLocales(port, MORE_THAN_DEFAULT_LIMIT);

      // then — this is the regression the fix addresses (#68071): if this ever starts
      // passing, the control case is no longer demonstrating the bug
      assertThat(response.statusCode())
          .as("without the fix, Tomcat rejects more than 10 Accept-Language values")
          .isEqualTo(500);
    }
  }

  /** Minimal Spring Boot context: embedded Tomcat, the production bean, and a stub controller. */
  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import(TomcatMaxLocaleConfig.class)
  static class WithConfig {

    @Bean
    public LocaleProbeController localeProbeController() {
      return new LocaleProbeController();
    }
  }

  /** Same context without {@link TomcatMaxLocaleConfig}: the control case. */
  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class WithoutConfig {

    @Bean
    public LocaleProbeController localeProbeController() {
      return new LocaleProbeController();
    }
  }

  /** Stub controller that answers 200 as long as the request got past the connector. */
  @RestController
  static class LocaleProbeController {

    @GetMapping("/locale-probe")
    public ResponseEntity<String> probe() {
      return ResponseEntity.ok("ok");
    }
  }
}
