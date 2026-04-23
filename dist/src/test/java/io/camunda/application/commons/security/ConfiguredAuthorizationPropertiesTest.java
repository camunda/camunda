/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@SpringBootTest(
    classes = ConfiguredAuthorizationPropertiesTest.TestConfig.class,
    properties = "spring.config.location=classpath:properties/application-authorization-props.yaml")
class ConfiguredAuthorizationPropertiesTest {

  @Autowired private CamundaSecurityProperties securityProperties;

  @Test
  void shouldLoadAuthorizationsFromYaml() {
    // when
    final var authorizations = securityProperties.getInitialization().getAuthorizations();

    // then
    assertThat(authorizations).hasSize(4);
  }

  @Test
  void shouldMapIdBasedWildcardAuthorization() {
    // when
    final var auth = securityProperties.getInitialization().getAuthorizations().getFirst();

    // then
    assertThat(auth.ownerType()).isEqualTo(AuthorizationOwnerType.USER);
    assertThat(auth.ownerId()).isEqualTo("john.doe");
    assertThat(auth.resourceType()).isEqualTo(AuthorizationResourceType.PROCESS_DEFINITION);
    assertThat(auth.resourceId()).isEqualTo("*");
    assertThat(auth.permissions())
        .containsExactlyInAnyOrder(
            PermissionType.READ_PROCESS_INSTANCE, PermissionType.CREATE_PROCESS_INSTANCE);
  }

  @Test
  void shouldMapIdBasedWithSpecificResourceIdAuthorization() {
    // when
    final var auth = securityProperties.getInitialization().getAuthorizations().get(1);

    // then
    assertThat(auth.ownerType()).isEqualTo(AuthorizationOwnerType.ROLE);
    assertThat(auth.ownerId()).isEqualTo("developers");
    assertThat(auth.resourceType()).isEqualTo(AuthorizationResourceType.PROCESS_DEFINITION);
    assertThat(auth.resourceId()).isEqualTo("order-process");
    assertThat(auth.permissions()).containsExactly(PermissionType.UPDATE_PROCESS_INSTANCE);
  }

  @Test
  void shouldFilterEmptyPermissionFromTrailingComma() {
    // when — simulates PERMISSIONS=READ,UPDATE, (trailing comma produces empty string)
    final var auth = securityProperties.getInitialization().getAuthorizations().get(2);

    // then — empty entry is filtered out, only valid permissions remain
    assertThat(auth.ownerId()).isEqualTo("trailing.comma");
    assertThat(auth.permissions())
        .containsExactlyInAnyOrder(PermissionType.READ, PermissionType.UPDATE);
  }

  @Test
  void shouldFilterWhitespaceOnlyPermission() {
    // when — simulates PERMISSIONS=READ, ,UPDATE (whitespace-only entry)
    final var auth = securityProperties.getInitialization().getAuthorizations().get(3);

    // then — whitespace entry is filtered out, only valid permissions remain
    assertThat(auth.ownerId()).isEqualTo("whitespace.entry");
    assertThat(auth.permissions())
        .containsExactlyInAnyOrder(PermissionType.READ, PermissionType.UPDATE);
  }

  @Configuration
  @Import({CamundaSecurityConfiguration.class})
  static class TestConfig {}
}
