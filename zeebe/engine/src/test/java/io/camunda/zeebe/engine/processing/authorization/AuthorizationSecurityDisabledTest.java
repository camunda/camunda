/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.authorization;

import static io.camunda.zeebe.protocol.record.value.AuthorizationScope.WILDCARD;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class AuthorizationSecurityDisabledTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldAuthorizeAuthorizationCommandWhenAuthorizationsAndMultiTenancyDisabled() {
    // given — engine with both authorization and multi-tenancy checks disabled (default)
    final var ownerId = UUID.randomUUID().toString();

    // when — command sent without any principal claims (no username, no client id)
    engine
        .authorization()
        .newAuthorization()
        .withOwnerId(ownerId)
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(AuthorizationResourceType.RESOURCE)
        .withResourceMatcher(WILDCARD.getMatcher())
        .withResourceId(WILDCARD.getResourceId())
        .withPermissions(PermissionType.CREATE)
        .create();

    // then — command is accepted, not rejected or errored
    assertThat(
            RecordingExporter.authorizationRecords(AuthorizationIntent.CREATED)
                .withOwnerId(ownerId)
                .exists())
        .isTrue();
  }
}
