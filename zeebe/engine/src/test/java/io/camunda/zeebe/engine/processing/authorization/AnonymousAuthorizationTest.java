/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.util.AuthorizationUtil;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class AnonymousAuthorizationTest {

  private static final String TEST_FORM_1 = "/form/test-form-2.form";
  private static final String FORM_ID_1 = "Form_6s1b76p";
  private static final String PROCESS_ID = "PROCESS";
  private static final String PROCESS_ID_WITH_NOT_EXISTING_FORM = "PROCESS_WITH_NOT_EXISTING_FORM";
  private static final String TENANT = "foo";
  private static String username;
  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", t -> t.zeebeJobType("test").zeebeJobRetries("5"))
          .endEvent()
          .done();
  private static final BpmnModelInstance PROCESS_WITH_NOT_EXISTING_FORM =
      Bpmn.createExecutableProcess(PROCESS_ID_WITH_NOT_EXISTING_FORM)
          .startEvent()
          .userTask("task")
          .zeebeFormId(FORM_ID_1)
          .endEvent()
          .done();

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withIdentitySetup()
          .withSecurityConfig(c -> c.getAuthorizations().setEnabled(true));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Before
  public void setUp() {
    username = UUID.randomUUID().toString();
    final var user = engine.user().newUser(username).create().getValue();
    final var tenantKey = engine.tenant().newTenant().withTenantId(TENANT).create().getKey();
    engine.tenant().addEntity(TENANT).withEntityId(username).withEntityType(EntityType.USER).add();

    engine
        .authorization()
        .newAuthorization()
        .withPermissions(PermissionType.CREATE)
        .withResourceType(AuthorizationResourceType.RESOURCE)
        .withResourceMatcher(AuthorizationResourceMatcher.ANY)
        .withResourceId("*")
        .withOwnerId(user.getUsername())
        .withOwnerType(AuthorizationOwnerType.USER)
        .create();

    engine.deployment().withXmlResource(PROCESS).deploy(username);
  }

  @Test
  public void shouldCreateProcessInstanceWithAnonymousClaim() {
    // given
    final var anonymous =
        AuthorizationUtil.getAuthInfoWithClaim(Authorization.AUTHORIZED_ANONYMOUS_USER, true);

    // when
    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(PROCESS_ID).create(anonymous);

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldNotCreateProcessInstanceWhenDisablingAnonymousClaim() {
    // given
    final var anonymousWithExplicitlyDisabledAnonymous =
        AuthorizationUtil.getAuthInfoWithClaim(Authorization.AUTHORIZED_ANONYMOUS_USER, false);

    // when
    engine
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .expectRejection()
        .create(anonymousWithExplicitlyDisabledAnonymous);

    // then
    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .onlyCommandRejections()
                .getFirst()
                .getRejectionType())
        .isEqualTo(RejectionType.FORBIDDEN);
  }

  @Test
  public void shouldNotCreateProcessInstanceWithoutAnonymousClaim() {
    // given
    final var anonymousWithExplicitlyDisabledAnonymous =
        AuthorizationUtil.getAuthInfoWithClaim("foo", "bar");

    // when
    engine
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .expectRejection()
        .create(anonymousWithExplicitlyDisabledAnonymous);

    // then
    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .onlyCommandRejections()
                .getFirst()
                .getRejectionType())
        .isEqualTo(RejectionType.FORBIDDEN);
  }

  @Test
  public void shouldResolveIncidentWithAnonymousClaim() {
    // given
    engine
        .deployment()
        .withXmlResource(PROCESS_WITH_NOT_EXISTING_FORM)
        .withTenantId(TENANT)
        .deploy(username);

    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID_WITH_NOT_EXISTING_FORM)
            .withTenantId(TENANT)
            .create();

    // assume
    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(incident).isNotNull();

    // create anonymous authorization
    final var anonymous =
        AuthorizationUtil.getAuthInfoWithClaim(Authorization.AUTHORIZED_ANONYMOUS_USER, true);

    // when
    // deploy missing form
    engine.deployment().withJsonClasspathResource(TEST_FORM_1).withTenantId(TENANT).deploy();
    // resolve incident by using anonymous authorization
    engine.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve(anonymous);

    // then
    assertThat(RecordingExporter.incidentRecords().onlyEvents().limit(2))
        .extracting(io.camunda.zeebe.protocol.record.Record::getKey, Record::getIntent)
        .describedAs("form not found incident is resolved and no new incident is created")
        .containsExactly(
            tuple(incident.getKey(), IncidentIntent.CREATED),
            tuple(incident.getKey(), IncidentIntent.RESOLVED));
  }
}
