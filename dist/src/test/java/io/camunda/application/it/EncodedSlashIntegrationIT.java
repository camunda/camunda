/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.rest.EncodedSlashMvcConfig;
import io.camunda.application.commons.rest.TomcatEncodedSlashConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Integration test for <a href="https://github.com/camunda/camunda/issues/45215">#45215</a>.
 * Verifies the full stack — Tomcat connector, Spring Security firewall, and Spring MVC path
 * matching — correctly handles {@code %2F} in URL path segments: the request reaches the controller
 * AND the {@code @PathVariable} receives the decoded value with the slash intact.
 *
 * <p>This boots a real embedded Tomcat with Spring Security enabled — NOT a MockMvc test, because
 * MockMvc bypasses Tomcat's {@code EncodedSolidusHandling} check.
 *
 * <p>The three production beans that must cooperate are:
 *
 * <ol>
 *   <li>{@link TomcatEncodedSlashConfig}: {@code PASS_THROUGH} — keeps {@code %2F} in the URI so
 *       Tomcat's path normalizer never sees a literal {@code /} from the encoded slash.
 *   <li>{@link EncodedSlashMvcConfig#encodedSlashFirewallCustomizer()}: {@code
 *       allowUrlEncodedSlash(true)} — prevents Spring Security's {@link
 *       org.springframework.security.web.firewall.StrictHttpFirewall} from rejecting {@code %2F}
 *       with 400. Defined unconditionally so it applies across all auth profiles.
 *   <li>{@link EncodedSlashMvcConfig}: {@code urlDecode=false} — prevents Spring MVC's {@link
 *       org.springframework.web.util.UrlPathHelper} from decoding {@code %2F} to {@code /} before
 *       path matching (which would introduce {@code //} and cause the path sanitizer to collapse it
 *       to {@code /}, stripping the leading slash). After matching, {@code decodePathVariables()}
 *       decodes the raw {@code %2FmyGroup} → {@code /myGroup} for the {@code @PathVariable}.
 * </ol>
 *
 * <p><b>Scope:</b> the Tomcat, firewall, and MVC configs live in the {@code dist} module without a
 * profile condition, so they apply to all auth profiles. Optimize defines independent Spring
 * Security configurations running in their own application contexts; an equivalent customizer would
 * be needed there if Optimize ever exposes path-variable entity IDs sourced from OIDC.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    classes = EncodedSlashIntegrationIT.TestConfig.class)
class EncodedSlashIntegrationIT {

  @LocalServerPort private int port;

  @Test
  void shouldAcceptEncodedSlashAtTomcatAndFirewallLayers() throws Exception {
    // given — a group ID with a forward slash, encoded as %2F (what the Identity UI sends)
    // when
    final var response = sendPut("/v2/roles/admin/groups/%2FmyGroup");

    // then — neither Tomcat nor Spring Security's StrictHttpFirewall rejects the request with 400
    assertThat(response.statusCode())
        .as(
            "Tomcat (PASS_THROUGH) and StrictHttpFirewall (allowUrlEncodedSlash) should let %2F through")
        .isNotEqualTo(400);
    assertThat(response.body())
        .as("@PathVariable should receive the decoded value with the slash intact")
        .isEqualTo("/myGroup");
  }

  @Test
  void shouldAcceptNestedEncodedSlashesAtTomcatAndFirewallLayers() throws Exception {
    // given — nested Keycloak group: /org/team/engineering
    final var response = sendPut("/v2/roles/admin/groups/%2Forg%2Fteam%2Fengineering");

    // then
    assertThat(response.statusCode()).isNotEqualTo(400);
  }

  @Test
  void shouldAcceptEncodedSlashOnGetEndpoint() throws Exception {
    // given
    final var response = sendGet("/v2/groups/%2FmyGroup");

    // then
    assertThat(response.statusCode()).isNotEqualTo(400);
  }

  @Test
  void shouldHandleNormalGroupIdWithoutSlash() throws Exception {
    // given — a normal group ID (no slashes) should still be routed and bound correctly
    final var response = sendPut("/v2/roles/admin/groups/normalGroup");

    // then
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("normalGroup");
  }

  private HttpResponse<String> sendPut(final String path) throws Exception {
    return sendRequest(path, "PUT");
  }

  private HttpResponse<String> sendGet(final String path) throws Exception {
    return sendRequest(path, "GET");
  }

  private HttpResponse<String> sendRequest(final String path, final String method)
      throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      final var request =
          HttpRequest.newBuilder()
              .uri(URI.create("http://localhost:" + port + path))
              .method(method, HttpRequest.BodyPublishers.noBody())
              .build();
      return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
  }

  /**
   * Minimal Spring Boot context: embedded Tomcat + Spring Security + test controller. Mirrors the
   * three production beans that must cooperate for encoded slashes to work:
   *
   * <ol>
   *   <li>{@link TomcatEncodedSlashConfig} — Tomcat connector layer (PASS_THROUGH)
   *   <li>{@link EncodedSlashMvcConfig} — Spring Security firewall + Spring MVC path matching
   * </ol>
   *
   * <p>The firewall customizer is no longer inline here; it lives in {@link EncodedSlashMvcConfig}
   * so it applies unconditionally across all auth profiles.
   */
  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EnableWebSecurity
  static class TestConfig {

    /** Tomcat layer: allow %2F through instead of rejecting with 400. */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> encodedSlashCustomizer() {
      return factory ->
          factory.addConnectorCustomizers(
              (TomcatConnectorCustomizer)
                  connector ->
                      connector.setEncodedSolidusHandling(
                          EncodedSolidusHandling.PASS_THROUGH.getValue()));
    }

    /** Spring MVC layer: don't pre-decode the URI before path matching. */
    @Bean
    public EncodedSlashMvcConfig encodedSlashMvcConfig() {
      return new EncodedSlashMvcConfig();
    }

    @Bean
    public SecurityFilterChain permitAll(final HttpSecurity http) throws Exception {
      return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
          .csrf(csrf -> csrf.disable())
          .build();
    }

    @Bean
    public EncodedSlashTestController encodedSlashTestController() {
      return new EncodedSlashTestController();
    }
  }

  /** Stub controller that echoes the decoded {@code @PathVariable} value. */
  @RestController
  static class EncodedSlashTestController {

    @PutMapping("/v2/roles/{roleId}/groups/{groupId}")
    public ResponseEntity<String> assignGroup(
        @PathVariable final String roleId, @PathVariable final String groupId) {
      return ResponseEntity.ok(groupId);
    }

    @GetMapping("/v2/groups/{groupId}")
    public ResponseEntity<String> getGroup(@PathVariable final String groupId) {
      return ResponseEntity.ok(groupId);
    }
  }
}
