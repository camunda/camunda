/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.identity;

import io.camunda.identity.sdk.authorizations.dto.Authorization;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class IdentityAuthorizationTest {

  @Test
  public void testCreateFromAuthorization() {
    String resourceKey = "key";
    String resourceType = "type";
    Set<String> permissions = Stream.of("read", "write")
            .collect(Collectors.toCollection(HashSet::new));
    Authorization authorization = new Authorization(resourceKey, resourceType, permissions);

    IdentityAuthorization identityAuthorization = IdentityAuthorization.createFrom(authorization);

    assertThat(identityAuthorization).isNotNull();
    assertThat(identityAuthorization.getResourceKey()).isEqualTo(resourceKey);
    assertThat(identityAuthorization.getResourceType()).isEqualTo(resourceType);
    assertThat(identityAuthorization.getPermissions().equals(authorization.getPermissions())).isTrue();

  }

  @Test
  public void testCreateFromNullAuthorizationsList() {
    List<IdentityAuthorization> identityAuthorizations =
            IdentityAuthorization.createFrom((List< Authorization>)null);

    assertThat(identityAuthorizations).isNotNull();
    assertThat(identityAuthorizations).isEmpty();
  }

  @Test
  public void testCreateFromEmptyAuthorizationsList() {
    List<IdentityAuthorization> identityAuthorizations =
            IdentityAuthorization.createFrom(new LinkedList<>());

    assertThat(identityAuthorizations).isNotNull();
    assertThat(identityAuthorizations).isEmpty();
  }

  @Test
  public void testCreateFromAuthorizationsList() {
    Set<String> permissions = Stream.of("read", "write")
            .collect(Collectors.toCollection(HashSet::new));

    Authorization firstAuthorization = new Authorization("key1", "type1", permissions);
    Authorization secondAuthorization = new Authorization("key2", "type2", permissions);
    List<Authorization> authorizationList = Stream.of(firstAuthorization, secondAuthorization).toList();

    List<IdentityAuthorization> identityAuthorizations = IdentityAuthorization.createFrom(authorizationList);

    assertThat(identityAuthorizations).isNotNull();
    assertThat(identityAuthorizations.size()).isEqualTo(2);
  }
}
