/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.zeebe.gateway.rest.RestControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;

@WebMvcTest(IncidentAlertingController.class)
public class IncidentAlertingControllerTest extends RestControllerTest {

  static final String BASE_URL = "/v2/incident-alerting";

  @Test
  void shouldSaveAndRetrieveAlertingConfig() {

    final var request =
        """
          {
             "filters": [{
                  "processDefinitionKey": "processDefinitionKey123"
               }],
             "channel": {
                "type": "email",
                "value": "test@example.com"
             }
           }
        """;

    // when/then
    webClient
        .post()
        .uri(BASE_URL + "/config")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk();

    // when/then
    webClient
        .get()
        .uri(BASE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
                [
                  {
                   "filters": [{
                        "processDefinitionKey": "processDefinitionKey123"
                     }],
                   "channel": {
                      "type": "email",
                      "value": "test@example.com"
                   }
                 }
                ]
                """);
  }
}
