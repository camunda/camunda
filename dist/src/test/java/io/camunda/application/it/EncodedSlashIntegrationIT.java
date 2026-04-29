/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.it;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.boot.tomcat.TomcatConnectorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Integration test for <a href="https://github.com/camunda/camunda/issues/45215">#45215</a>.
 * Verifies that encoded forward slashes ({@code %2F}) in URL path variables pass through all layers
 * (Tomcat connector, Spring Security firewall, Spring MVC) and are decoded correctly.
 *
 * <p>This boots a real embedded Tomcat with Spring Security enabled — NOT a MockMvc test, because
 * MockMvc bypasses Tomcat's {@code EncodedSolidusHandling} check.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    classes = EncodedSlashIntegrationIT.TestConfig.class,
    properties = "server.tomcat.encoded-solidus-handling=passthrough")
class EncodedSlashIntegrationIT {

  @LocalServerPort private int port;

  @Test
  void shouldPassEncodedSlashThroughTomcatAndSecurityFirewall() throws Exception {
    // given — a group ID with a forward slash, encoded as %2F (what the Identity UI sends)
    // when
    final var response = sendPut("/v2/roles/admin/groups/%2FmyGroup");

    // then — request passes through Tomcat + StrictHttpFirewall and reaches the controller
    assertThat(response.statusCode())
        .as("Encoded slash should not be blocked by Tomcat or Spring Security firewall")
        .isEqualTo(200);
    // @PathVariable decodes %2F → /
    assertThat(response.body()).isEqualTo("/myGroup");
  }

  @Test
  void shouldPassNestedEncodedSlashes() throws Exception {
    // given — nested Keycloak group: /org/team/engineering
    final var response = sendPut("/v2/roles/admin/groups/%2Forg%2Fteam%2Fengineering");

    // then
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("/org/team/engineering");
  }

  @Test
  void shouldPassEncodedSlashOnGetEndpoint() throws Exception {
    // given
    final var response = sendGet("/v2/groups/%2FmyGroup");

    // then
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("/myGroup");
  }

  @Test
  void shouldPassNormalGroupIdWithoutSlash() throws Exception {
    // given — a normal group ID (no slashes) should still work
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
   * two production beans that must cooperate for encoded slashes to work:
   *
   * <ol>
   *   <li>{@link TomcatEncodedSlashConfig} — Tomcat connector layer
   *   <li>{@code WebSecurityConfig.encodedSlashFirewallCustomizer()} — Spring Security layer
   * </ol>
   *
   * <p>We don't import the full {@code WebSecurityConfig} because it requires the entire Camunda
   * service layer. Instead we replicate just the firewall customizer bean inline.
   */
  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EnableWebSecurity
  static class TestConfig {

    /** Tomcat layer: allow %2F through instead of rejecting with 400. */
    @Bean
    public TomcatConnectorCustomizer encodedSlashConnectorCustomizer() {
      return connector -> {
        // DECODE converts %2F → / at the Tomcat level (before Spring MVC)
        // PASS_THROUGH keeps %2F in the URI but Tomcat 10.1+ still rejects it during path
        // normalization. DECODE avoids this by decoding before the check.
        connector.setEncodedSolidusHandling(EncodedSolidusHandling.DECODE.getValue());
      };
    }

    /** Spring Security layer: allow %2F through the firewall. */
    @Bean
    public WebSecurityCustomizer encodedSlashFirewallCustomizer() {
      final var firewall = new StrictHttpFirewall();
      firewall.setAllowUrlEncodedSlash(true);
      return web -> web.httpFirewall(firewall);
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
