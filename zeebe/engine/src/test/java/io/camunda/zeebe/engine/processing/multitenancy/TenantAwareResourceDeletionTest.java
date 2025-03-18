/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.util.AuthorizationUtil;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordAssert;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class TenantAwareResourceDeletionTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withSecurityConfig(config -> config.getMultiTenancy().setEnabled(true));

  private static final String DRG_SINGLE_DECISION = "/dmn/decision-table.dmn";
  private static final String TEST_FORM_1 = "/form/test-form-1.form";
  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess("test").startEvent().endEvent().done();

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private final String tenantIdA = "tenant-a";
  private final String tenantIdB = "tenant-b";

  @Test
  public void shouldDeleteProcessForAuthorizedTenant() {
    // given
    final var deployment =
        ENGINE.deployment().withXmlResource(PROCESS).withTenantId(tenantIdA).deploy();
    final var resourceKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    // when
    final var deleted =
        ENGINE
            .resourceDeletion()
            .withResourceKey(resourceKey)
            .withAuthorizedTenantIds(tenantIdA)
            .delete();

    // then
    Assertions.assertThat(deleted.getValue()).hasTenantId(tenantIdA);
    verifyResourceIsDeleted(resourceKey);
  }

  @Test
  public void shouldDeleteDecisionForAuthorizedTenant() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlClasspathResource(DRG_SINGLE_DECISION)
            .withTenantId(tenantIdA)
            .deploy();
    final var resourceKey =
        deployment.getValue().getDecisionRequirementsMetadata().get(0).getDecisionRequirementsKey();

    // when
    final var deleted =
        ENGINE
            .resourceDeletion()
            .withResourceKey(resourceKey)
            .withAuthorizedTenantIds(tenantIdA)
            .delete();

    // then
    Assertions.assertThat(deleted.getValue()).hasTenantId(tenantIdA);
    verifyResourceIsDeleted(resourceKey);
  }

  @Test
  public void shouldDeleteFormForAuthorizedTenant() {
    // given
    final var deployment =
        ENGINE.deployment().withXmlClasspathResource(TEST_FORM_1).withTenantId(tenantIdA).deploy();
    final var resourceKey = deployment.getValue().getFormMetadata().get(0).getFormKey();

    // when
    final var deleted =
        ENGINE
            .resourceDeletion()
            .withResourceKey(resourceKey)
            .withAuthorizedTenantIds(tenantIdA)
            .delete();

    // then
    Assertions.assertThat(deleted.getValue()).hasTenantId(tenantIdA);
    verifyResourceIsDeleted(resourceKey);
  }

  @Test
  public void shouldNotDeleteResourceForUnauthorizedTenant() {
    // given
    final var deployment =
        ENGINE.deployment().withXmlResource(PROCESS).withTenantId(tenantIdA).deploy();
    final var resourceKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    // when
    final var rejection =
        ENGINE
            .resourceDeletion()
            .withResourceKey(resourceKey)
            .withAuthorizedTenantIds(tenantIdB)
            .expectRejection()
            .delete();

    // then
    RecordAssert.assertThat(rejection)
        .describedAs("Expect resource is not found")
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to delete resource but no resource found with key `%d`"
                .formatted(resourceKey));
    assertThat(
            RecordingExporter.resourceDeletionRecords()
                .withIntent(ResourceDeletionIntent.DELETED)
                .exists())
        .isFalse();
  }

  @Test
  public void shouldDeleteResourceAssignedToDefaultTenantWithAnonymousUser() {
    // given
    ENGINE.tenant().newTenant().withTenantId(tenantIdA).create();
    ENGINE.tenant().newTenant().withTenantId(tenantIdB).create();

    final var deployment =
        ENGINE.deployment().withXmlResource(PROCESS).withTenantId(tenantIdA).deploy();
    final var resourceKey =
        deployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    final var anonymous =
        AuthorizationUtil.getAuthInfoWithClaim(Authorization.AUTHORIZED_ANONYMOUS_USER, true);

    // when
    final var deleted = ENGINE.resourceDeletion().withResourceKey(resourceKey).delete(anonymous);

    // then
    Assertions.assertThat(deleted.getValue()).hasTenantId(tenantIdA);
    verifyResourceIsDeleted(resourceKey);
  }

  @Test
  public void shouldDeleteResourceWithAnonymousUser() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(PROCESS)
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .deploy();
    final var resourceKey =
        deployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    final var anonymous =
        AuthorizationUtil.getAuthInfoWithClaim(Authorization.AUTHORIZED_ANONYMOUS_USER, true);

    // when
    final var deleted = ENGINE.resourceDeletion().withResourceKey(resourceKey).delete(anonymous);

    // then
    Assertions.assertThat(deleted.getValue()).hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    verifyResourceIsDeleted(resourceKey);
  }

  private void verifyResourceIsDeleted(final long key) {
    assertThat(
            RecordingExporter.resourceDeletionRecords()
                .limit(r -> r.getIntent().equals(ResourceDeletionIntent.DELETED)))
        .describedAs("Expect resource to be deleted")
        .extracting(Record::getIntent, r -> r.getValue().getResourceKey())
        .containsOnly(
            tuple(ResourceDeletionIntent.DELETE, key),
            tuple(ResourceDeletionIntent.DELETING, key),
            tuple(ResourceDeletionIntent.DELETED, key));
  }
}
