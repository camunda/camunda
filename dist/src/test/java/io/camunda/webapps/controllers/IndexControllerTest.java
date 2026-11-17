/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.webapps.WebappsModuleConfiguration.WebappsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class IndexControllerTest {

  private final IndexController controller =
      new IndexController(new WebappsProperties(true, "operate", false));

  @Test
  void shouldRedirectLoginNavigationToDefaultApp() {
    // given
    final var request = new MockHttpServletRequest("GET", "/login");
    request.setQueryString("next=/tasklist");
    request.addHeader("Sec-Fetch-Mode", "navigate");

    // when
    final var response = controller.login(request);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(response.getHeaders().getLocation()).hasToString("/operate/login?next=/tasklist");
  }

  @Test
  void shouldAnswerLoginRequestOfCsrfTokenWithoutRedirect() {
    // given a request that is not a browser navigation, so it cannot read the headers of a
    // redirect it follows
    final var request = new MockHttpServletRequest("GET", "/login");
    request.addHeader("Sec-Fetch-Mode", "cors");

    // when
    final var response = controller.login(request);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(response.getHeaders().getLocation()).isNull();
  }

  @Test
  void shouldAnswerLoginRequestWithoutFetchMetadataWithoutRedirect() {
    // given a caller that sends no fetch metadata at all, such as a test or CLI client
    final var request = new MockHttpServletRequest("GET", "/login");

    // when
    final var response = controller.login(request);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void shouldRedirectOldProcessesRouteToDefaultApp() {
    // given
    final var request = new MockHttpServletRequest("GET", "/processes");

    // when
    final var view = controller.redirectOldProcessesRoute(request);

    // then
    assertThat(view).isEqualTo("redirect:/operate/processes");
  }
}
