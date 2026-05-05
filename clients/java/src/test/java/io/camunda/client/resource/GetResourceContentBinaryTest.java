/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.client.resource;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import io.camunda.client.util.RestGatewayService;
import io.camunda.client.util.assertions.LoggedRequestAssert;
import org.junit.jupiter.api.Test;

class GetResourceContentBinaryTest extends ClientRestTest {

  @Test
  void shouldGetResourceContentBinary() {
    // given
    final String resourceContent = "binary content";
    gatewayService.onResourceContentBinaryGetRequest(123L, resourceContent);

    // when
    final String response = client.newResourceContentBinaryGetRequest(123L).execute();

    // then
    LoggedRequestAssert.assertThat(RestGatewayService.getLastRequest())
        .hasMethod(RequestMethod.GET)
        .hasUrl(RestGatewayPaths.getResourceContentBinaryUrl("123"));

    assertThat(response).isEqualTo(resourceContent);
  }
}
