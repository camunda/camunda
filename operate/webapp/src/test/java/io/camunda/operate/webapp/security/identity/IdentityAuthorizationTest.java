/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.identity.sdk.authorizations.dto.Authorization;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class IdentityAuthorizationTest {

  @Test
  public void testCreateFromAuthorization() {
    final String resourceKey = "key";
    final String resourceType = "type";
    final Set<String> permissions =
        Stream.of("read", "write").collect(Collectors.toCollection(HashSet::new));
    final Authorization authorization = new Authorization(resourceKey, resourceType, permissions);

    final IdentityAuthorization identityAuthorization =
        IdentityAuthorization.createFrom(authorization);

    assertThat(identityAuthorization).isNotNull();
    assertThat(identityAuthorization.getResourceKey()).isEqualTo(resourceKey);
    assertThat(identityAuthorization.getResourceType()).isEqualTo(resourceType);
    assertThat(identityAuthorization.getPermissions().equals(authorization.getPermissions()))
        .isTrue();
  }

  @Test
  public void testCreateFromNullAuthorizationsList() {
    final List<IdentityAuthorization> identityAuthorizations =
        IdentityAuthorization.createFrom((List<Authorization>) null);

    assertThat(identityAuthorizations).isNotNull();
    assertThat(identityAuthorizations).isEmpty();
  }

  @Test
  public void testCreateFromEmptyAuthorizationsList() {
    final List<IdentityAuthorization> identityAuthorizations =
        IdentityAuthorization.createFrom(new LinkedList<>());

    assertThat(identityAuthorizations).isNotNull();
    assertThat(identityAuthorizations).isEmpty();
  }

  @Test
  public void testCreateFromAuthorizationsList() {
    final Set<String> permissions =
        Stream.of("read", "write").collect(Collectors.toCollection(HashSet::new));

    final Authorization firstAuthorization = new Authorization("key1", "type1", permissions);
    final Authorization secondAuthorization = new Authorization("key2", "type2", permissions);
    final List<Authorization> authorizationList =
        Stream.of(firstAuthorization, secondAuthorization).toList();

    final List<IdentityAuthorization> identityAuthorizations =
        IdentityAuthorization.createFrom(authorizationList);

    assertThat(identityAuthorizations).isNotNull();
    assertThat(identityAuthorizations.size()).isEqualTo(2);
  }
}
