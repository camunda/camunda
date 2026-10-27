/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.controllers;

import java.util.Iterator;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public class OidcMockMvcTestHelper {

  /**
   * Extends {@link SecurityMockMvcRequestPostProcessors#oidcLogin()} by consistently registering
   * the generated {@link OAuth2AuthorizedClient} in the provided {@link
   * OAuth2AuthorizedClientRepository}.
   *
   * <p>{@link SecurityMockMvcRequestPostProcessors#oidcLogin()} creates an {@link
   * OAuth2AuthorizedClient} in {@link
   * org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OAuth2ClientRequestPostProcessor#postProcessRequest(MockHttpServletRequest)}.
   * This client is registered in an instance of <code>
   * org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OAuth2ClientRequestPostProcessor.TestOAuth2AuthorizedClientRepository
   * </code>, which is supposed to be a wrapper around the actual {@link
   * OAuth2AuthorizedClientRepository} under test and which stores the authorized client in a
   * request attribute of the mock http request. However, the wrapper is not properly registered in
   * the application context, meaning that other beans that depend on {@link
   * OAuth2AuthorizedClientRepository} do not get the wrapper injected and accordingly are not able
   * to access the authorized client object. This class here extends this procedure by also
   * registering this test object in a provided instance of {@link
   * OAuth2AuthorizedClientRepository}.
   *
   * <p>Since {@link SecurityMockMvcRequestPostProcessors#oidcLogin()} doesn't expose the objects it
   * creates, we have to access it in a fairly hacky way, see implementation.
   */
  public static RequestPostProcessor oidcLogin(
      final OAuth2AuthorizedClientRepository authorizedClientRepository) {
    return (request) -> {
      request = SecurityMockMvcRequestPostProcessors.oidcLogin().postProcessRequest(request);

      final OAuth2AuthorizedClient client = findAuthorizedClientInRequestAttributes(request);
      authorizedClientRepository.saveAuthorizedClient(
          client, null, request, new MockHttpServletResponse());

      return request;
    };
  }

  private static OAuth2AuthorizedClient findAuthorizedClientInRequestAttributes(
      final MockHttpServletRequest request) {
    final Iterator<String> attributeIterator = request.getAttributeNames().asIterator();

    OAuth2AuthorizedClient client = null;

    while (attributeIterator.hasNext()) {
      final String attributeName = attributeIterator.next();
      final Object attribute = request.getAttribute(attributeName);

      if (attribute instanceof OAuth2AuthorizedClient) {
        if (client == null) {
          client = (OAuth2AuthorizedClient) attribute;

        } else {
          throw new RuntimeException(
              "Multiple OAuth2AuthorizedClient objects in request attributes found");
        }
      }
    }

    if (client == null) {
      throw new RuntimeException("No OAuth2AuthorizedClient in request attributes found");
    }
    return client;
  }
}
