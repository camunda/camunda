/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class GroupIdPathResolverTest {

  private static final String FALLBACK = "fallback";
  private static final String ROLES_PREFIX = "/v2/roles/admin/groups/";

  private HttpServletRequest requestWith(final String contextPath, final String uri) {
    final var request = mock(HttpServletRequest.class);
    when(request.getContextPath()).thenReturn(contextPath);
    when(request.getRequestURI()).thenReturn(uri);
    return request;
  }

  @Test
  void shouldReturnPlainGroupId() {
    final var request = requestWith("", "/v2/roles/admin/groups/myGroup");
    assertThat(GroupIdPathResolver.resolveGroupId(request, ROLES_PREFIX, FALLBACK))
        .isEqualTo("myGroup");
  }

  @Test
  void shouldDecodePercentEncodedLeadingSlash() {
    final var request = requestWith("", "/v2/roles/admin/groups/%2FmyGroup");
    assertThat(GroupIdPathResolver.resolveGroupId(request, ROLES_PREFIX, FALLBACK))
        .isEqualTo("/myGroup");
  }

  @Test
  void shouldDecodeGroupIdContainingGroupsSubstring() {
    // lastIndexOf("/groups/") would incorrectly match inside the group ID;
    // indexOf on the exact prefix avoids this.
    final var request = requestWith("", "/v2/roles/admin/groups/%2Ffoo%2Fgroups%2Fbar");
    assertThat(GroupIdPathResolver.resolveGroupId(request, ROLES_PREFIX, FALLBACK))
        .isEqualTo("/foo/groups/bar");
  }

  @Test
  void shouldNotTranslatePlusToSpace() {
    // URLDecoder.decode() converts '+' to space; UriUtils.decode() does not.
    final var request = requestWith("", "/v2/roles/admin/groups/foo+bar");
    assertThat(GroupIdPathResolver.resolveGroupId(request, ROLES_PREFIX, FALLBACK))
        .isEqualTo("foo+bar");
  }

  @Test
  void shouldReturnFallbackWhenPrefixNotFound() {
    final var request = requestWith("", "/v2/roles/admin/users/someUser");
    assertThat(GroupIdPathResolver.resolveGroupId(request, ROLES_PREFIX, FALLBACK))
        .isEqualTo(FALLBACK);
  }

  @Test
  void shouldRespectNonEmptyContextPath() {
    final var request = requestWith("/camunda", "/camunda/v2/roles/admin/groups/%2FmyGroup");
    assertThat(GroupIdPathResolver.resolveGroupId(request, ROLES_PREFIX, FALLBACK))
        .isEqualTo("/myGroup");
  }
}
