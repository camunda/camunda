/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.client.protocol.rest.PermissionTypeEnum;
import io.camunda.zeebe.client.protocol.rest.ResourceTypeEnum;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class AddPermissionsTest {

  ZeebeClient client;

  @TestZeebe
  final TestStandaloneBroker zeebe = new TestStandaloneBroker().withRecordingExporter(true);

  ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  void init() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @Test
  void shouldAddPermissionsToOwner() {
    // given
    final long ownerKey = createUser();

    // when
    client
        .newAddPermissionsCommand(ownerKey)
        .resourceType(ResourceTypeEnum.DEPLOYMENT)
        .permission(PermissionTypeEnum.CREATE)
        .resourceId("resourceId")
        .send()
        .join();

    // then
    final var recordValue =
        RecordingExporter.authorizationRecords(AuthorizationIntent.PERMISSION_ADDED)
            .withOwnerKey(ownerKey)
            .limit(2)
            .getLast()
            .getValue();
    assertThat(recordValue.getResourceType()).isEqualTo(AuthorizationResourceType.DEPLOYMENT);
    assertThat(recordValue.getOwnerType()).isEqualTo(AuthorizationOwnerType.USER);
    final var permission = recordValue.getPermissions().getFirst();
    assertThat(permission.getPermissionType()).isEqualTo(PermissionType.CREATE);
    assertThat(permission.getResourceIds()).containsExactly("resourceId");
  }

  @Test
  void shouldRejectWhenOwnerNotFound() {
    // given
    final var nonExistingOwnerKey = 1L;

    // when
    final var future =
        client
            .newAddPermissionsCommand(nonExistingOwnerKey)
            .resourceType(ResourceTypeEnum.DEPLOYMENT)
            .permission(PermissionTypeEnum.CREATE)
            .resourceId("resourceId")
            .send();

    // then
    assertThatThrownBy(future::join)
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("title: NOT_FOUND")
        .hasMessageContaining("status: 404")
        .hasMessageContaining(
            "Expected to find owner with key: '%d', but none was found"
                .formatted(nonExistingOwnerKey));
  }

  private long createUser() {
    return client
        .newUserCreateCommand()
        .username("foo")
        .name("Foo Bar")
        .email("bar@baz.com")
        .password("zabraboof")
        .send()
        .join()
        .getUserKey();
  }
}
