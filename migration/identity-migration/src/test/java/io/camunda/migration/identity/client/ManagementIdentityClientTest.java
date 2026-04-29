/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class ManagementIdentityClientTest {

  @Test
  void fetchUsersIncludesConfiguredResultSize() {
    final var restTemplate = new RestTemplate();
    final var server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(requestTo("/api/users?page=0&resultSize=250"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    final var client = new ManagementIdentityClient(restTemplate, "org-1", 250, 100);

    assertThat(client.fetchUsers(0)).isEmpty();
    server.verify();
  }

  @Test
  void fetchGroupsIncludesConfiguredResultSizeAndOrgId() {
    final var restTemplate = new RestTemplate();
    final var server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(requestTo("/api/groups?page=2&organizationId=org-1&resultSize=500"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    final var client = new ManagementIdentityClient(restTemplate, "org-1", 100, 500);

    assertThat(client.fetchGroups(2)).isEmpty();
    server.verify();
  }
}
