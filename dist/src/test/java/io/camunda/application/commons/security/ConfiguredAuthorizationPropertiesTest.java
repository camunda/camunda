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
import io.camunda.security.configuration.ConfiguredAuthorization;
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
    assertThat(authorizations).hasSize(5);
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
    assertThat(auth.resourcePropertyName()).isNull();
    assertThat(auth.permissions())
        .containsExactlyInAnyOrder(
            PermissionType.READ_PROCESS_INSTANCE, PermissionType.CREATE_PROCESS_INSTANCE);
    assertThat(auth.isIdBased()).isTrue();
    assertThat(auth.isPropertyBased()).isFalse();
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
    assertThat(auth.resourcePropertyName()).isNull();
    assertThat(auth.permissions()).containsExactly(PermissionType.UPDATE_PROCESS_INSTANCE);
    assertThat(auth.isIdBased()).isTrue();
    assertThat(auth.isPropertyBased()).isFalse();
  }

  @Test
  void shouldMapPropertyBasedAuthorization() {
    // when
    final var auth = securityProperties.getInitialization().getAuthorizations().get(2);

    // then
    assertThat(auth.ownerType()).isEqualTo(AuthorizationOwnerType.USER);
    assertThat(auth.ownerId()).isEqualTo("jane.doe");
    assertThat(auth.resourceType()).isEqualTo(AuthorizationResourceType.USER_TASK);
    assertThat(auth.resourceId()).isNull();
    assertThat(auth.resourcePropertyName()).isEqualTo("assignee");
    assertThat(auth.permissions())
        .containsExactlyInAnyOrder(PermissionType.UPDATE, PermissionType.READ);
    assertThat(auth.isIdBased()).isFalse();
    assertThat(auth.isPropertyBased()).isTrue();
  }

  @Test
  void shouldMapInvalidAuthorizationWithBothResourceIdAndPropertyName() {
    // Note: This invalid authorization is successfully mapped from YAML.
    // The mutual exclusivity violation will be detected later during validation
    // by AuthorizationConfigurer

    // when
    final ConfiguredAuthorization auth =
        securityProperties.getInitialization().getAuthorizations().get(3);

    // then
    assertThat(auth.ownerType()).isEqualTo(AuthorizationOwnerType.USER);
    assertThat(auth.ownerId()).isEqualTo("mark.smith");
    assertThat(auth.resourceType()).isEqualTo(AuthorizationResourceType.USER_TASK);
    assertThat(auth.resourceId()).isEqualTo("some-task");
    assertThat(auth.resourcePropertyName()).isEqualTo("assignee");
    assertThat(auth.permissions()).containsExactly(PermissionType.READ);
    // Both helper methods return true, indicating the invalid state
    assertThat(auth.isIdBased()).isTrue();
    assertThat(auth.isPropertyBased()).isTrue();
  }

  @Test
  void shouldMapInvalidAuthorizationWithNeitherResourceIdNorPropertyName() {
    // Note: This invalid authorization is successfully mapped from YAML.
    // The missing resource identifier violation will be detected later during validation
    // by AuthorizationConfigurer

    // when
    final ConfiguredAuthorization auth =
        securityProperties.getInitialization().getAuthorizations().get(4);

    // then
    assertThat(auth.ownerType()).isEqualTo(AuthorizationOwnerType.ROLE);
    assertThat(auth.ownerId()).isEqualTo("sales-team");
    assertThat(auth.resourceType()).isEqualTo(AuthorizationResourceType.PROCESS_DEFINITION);
    assertThat(auth.resourceId()).isNull();
    assertThat(auth.resourcePropertyName()).isNull();
    assertThat(auth.permissions()).containsExactly(PermissionType.CREATE_PROCESS_INSTANCE);
    // Both helper methods return false, indicating the invalid state
    assertThat(auth.isIdBased()).isFalse();
    assertThat(auth.isPropertyBased()).isFalse();
  }

  @Configuration
  @Import({CamundaSecurityConfiguration.class})
  static class TestConfig {}
}
