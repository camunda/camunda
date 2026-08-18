/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.agentdefinition;

import static io.camunda.it.rdbms.db.fixtures.AgentDefinitionFixtures.createAndSaveRandomAgentDefinition;
import static io.camunda.it.rdbms.db.fixtures.AgentDefinitionFixtures.createAndSaveRandomAgentDefinitions;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.resourceAccessChecksFromResourceIds;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.resourceAccessChecksFromTenantIds;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.domain.AgentDefinitionDbModel;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.AgentDefinitionEntity;
import io.camunda.search.filter.AgentDefinitionFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AgentDefinitionQuery;
import io.camunda.search.sort.AgentDefinitionSort;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.core.authz.ResourceAccessChecks;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class AgentDefinitionIT {

  @TestTemplate
  public void shouldCreateAndGetAgentDefinitionByKey(
      final CamundaRdbmsTestApplication testApplication) {
    final AgentDefinitionDbModel model =
        createAndSaveRandomAgentDefinition(testApplication, b -> b);

    final var entity =
        testApplication
            .getRdbmsService()
            .getAgentDefinitionDbReader()
            .getByKey(model.agentDefinitionKey(), ResourceAccessChecks.disabled());

    assertThat(entity).isNotNull();
    assertFieldsMatch(model, entity);
  }

  @TestTemplate
  public void shouldReadBackNullVersionTagAsNull(
      final CamundaRdbmsTestApplication testApplication) {
    final AgentDefinitionDbModel model =
        createAndSaveRandomAgentDefinition(
            testApplication, b -> b.processDefinitionVersionTag(null));

    final var entity =
        testApplication
            .getRdbmsService()
            .getAgentDefinitionDbReader()
            .getByKey(model.agentDefinitionKey(), ResourceAccessChecks.disabled());

    assertThat(entity).isNotNull();
    assertThat(entity.processDefinitionVersionTag()).isNull();
  }

  @TestTemplate
  public void shouldReturnNullForUnknownKey(final CamundaRdbmsTestApplication testApplication) {
    final var entity =
        testApplication
            .getRdbmsService()
            .getAgentDefinitionDbReader()
            .getByKey(nextKey(), ResourceAccessChecks.disabled());

    assertThat(entity).isNull();
  }

  @TestTemplate
  public void shouldFindAllAgentDefinitionsPaged(
      final CamundaRdbmsTestApplication testApplication) {
    final String tenantId = "tenant-paged-" + nextStringId();
    createAndSaveRandomAgentDefinitions(testApplication, 20, b -> b.tenantId(tenantId));

    final var result =
        testApplication
            .getRdbmsService()
            .getAgentDefinitionDbReader()
            .search(
                new AgentDefinitionQuery(
                    new AgentDefinitionFilter.Builder().tenantIds(tenantId).build(),
                    AgentDefinitionSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))),
                ResourceAccessChecks.disabled());

    assertThat(result).isNotNull();
    assertThat(result.total()).isEqualTo(20);
    assertThat(result.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAgentDefinitionByAuthorizedResourceId(
      final CamundaRdbmsTestApplication testApplication) {
    final AgentDefinitionDbModel model =
        createAndSaveRandomAgentDefinition(testApplication, b -> b);
    createAndSaveRandomAgentDefinition(testApplication, b -> b);

    final var result =
        testApplication
            .getRdbmsService()
            .getAgentDefinitionDbReader()
            .search(
                AgentDefinitionQuery.of(b -> b),
                resourceAccessChecksFromResourceIds(
                    AuthorizationResourceType.PROCESS_DEFINITION, model.processDefinitionId()));

    assertThat(result).isNotNull();
    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().agentDefinitionKey())
        .isEqualTo(model.agentDefinitionKey());
  }

  @TestTemplate
  public void shouldFindAgentDefinitionByAuthorizedTenantId(
      final CamundaRdbmsTestApplication testApplication) {
    final AgentDefinitionDbModel model =
        createAndSaveRandomAgentDefinition(testApplication, b -> b);
    createAndSaveRandomAgentDefinition(testApplication, b -> b);

    final var result =
        testApplication
            .getRdbmsService()
            .getAgentDefinitionDbReader()
            .search(
                AgentDefinitionQuery.of(b -> b),
                resourceAccessChecksFromTenantIds(model.tenantId()));

    assertThat(result).isNotNull();
    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().agentDefinitionKey())
        .isEqualTo(model.agentDefinitionKey());
  }

  private void assertFieldsMatch(
      final AgentDefinitionDbModel dbModel, final AgentDefinitionEntity entity) {
    assertThat(entity.agentDefinitionKey()).isEqualTo(dbModel.agentDefinitionKey());
    assertThat(entity.agentType()).isEqualTo(dbModel.agentType());
    assertThat(entity.name()).isEqualTo(dbModel.name());
    assertThat(entity.elementId()).isEqualTo(dbModel.elementId());
    assertThat(entity.processDefinitionId()).isEqualTo(dbModel.processDefinitionId());
    assertThat(entity.processDefinitionKey()).isEqualTo(dbModel.processDefinitionKey());
    assertThat(entity.processDefinitionVersion()).isEqualTo(dbModel.processDefinitionVersion());
    assertThat(entity.processDefinitionVersionTag())
        .isEqualTo(dbModel.processDefinitionVersionTag());
    assertThat(entity.tenantId()).isEqualTo(dbModel.tenantId());
  }
}
