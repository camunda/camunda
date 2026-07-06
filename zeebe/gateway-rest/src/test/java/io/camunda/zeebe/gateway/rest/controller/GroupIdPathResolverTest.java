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

  private HttpServletRequest requestWith(final String uri) {
    final var request = mock(HttpServletRequest.class);
    when(request.getRequestURI()).thenReturn(uri);
    return request;
  }

  @Test
  void shouldReturnPlainGroupId() {
    final var request = requestWith("/v2/roles/admin/groups/myGroup");
    assertThat(GroupIdPathResolver.resolveGroupId(request, FALLBACK)).isEqualTo("myGroup");
  }

  @Test
  void shouldDecodePercentEncodedLeadingSlash() {
    final var request = requestWith("/v2/roles/admin/groups/%2FmyGroup");
    assertThat(GroupIdPathResolver.resolveGroupId(request, FALLBACK)).isEqualTo("/myGroup");
  }

  @Test
  void shouldDecodeGroupIdContainingGroupsSubstring() {
    // In the raw percent-encoded URI, slashes within path variable values are %2F, not "/".
    // So "%2Fgroups%2F" does not produce a literal "/groups/" — lastIndexOf is unambiguous.
    final var request = requestWith("/v2/roles/admin/groups/%2Ffoo%2Fgroups%2Fbar");
    assertThat(GroupIdPathResolver.resolveGroupId(request, FALLBACK)).isEqualTo("/foo/groups/bar");
  }

  @Test
  void shouldNotTranslatePlusToSpace() {
    // URLDecoder.decode() converts '+' to space; UriUtils.decode() does not.
    final var request = requestWith("/v2/roles/admin/groups/foo+bar");
    assertThat(GroupIdPathResolver.resolveGroupId(request, FALLBACK)).isEqualTo("foo+bar");
  }

  @Test
  void shouldReturnFallbackWhenNoGroupsSegment() {
    final var request = requestWith("/v2/roles/admin/users/someUser");
    assertThat(GroupIdPathResolver.resolveGroupId(request, FALLBACK)).isEqualTo(FALLBACK);
  }

  @Test
  void shouldHandleEncodedContainerId() {
    // Container ID containing special characters (e.g. "<default>") is percent-encoded in the
    // raw URI; lastIndexOf("/groups/") finds the structural separator regardless.
    final var request = requestWith("/v2/tenants/%3Cdefault%3E/groups/%2FmyGroup");
    assertThat(GroupIdPathResolver.resolveGroupId(request, FALLBACK)).isEqualTo("/myGroup");
  }
}
