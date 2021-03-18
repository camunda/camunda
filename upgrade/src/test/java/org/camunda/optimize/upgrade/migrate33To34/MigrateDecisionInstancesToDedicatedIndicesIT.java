/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate33To34;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.factories.Upgrade33To34PlanFactory;
import org.camunda.optimize.upgrade.plan.indices.DecisionInstanceIndexV4Old;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getDecisionInstanceIndexAliasName;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_MULTI_ALIAS;

public class MigrateDecisionInstancesToDedicatedIndicesIT extends AbstractMigrateInstanceIndicesIT {

  @SneakyThrows
  @Test
  public void decisionInstancesAreMigratedToDedicatedIndices() {
    // given
    executeBulk("steps/3.3/decision/33-decision-instances.json");
    final String definitionKey1 = "berStatusEvaluation";
    final String definitionKey2 = "invoiceClassification";
    final UpgradePlan upgradePlan = new Upgrade33To34PlanFactory().createUpgradePlan(prefixAwareClient);

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    // the old instance index has been deleted
    assertThat(indexExists(new DecisionInstanceIndexV4Old())).isFalse();

    // the new instance indices exist for all definitions with instances
    assertThat(indexExists(new DecisionInstanceIndex(definitionKey1))).isTrue();
    assertThat(indexExists(new DecisionInstanceIndex(definitionKey2))).isTrue();

    // all instances have been moved to the correct indices (and they can be reached with the correct alias)
    assertThat(getAllDocumentsOfIndexAs(getDecisionInstanceIndexAliasName(definitionKey1), DecisionInstanceDto.class))
      .hasSize(2)
      .extracting(DecisionInstanceDto::getDecisionDefinitionKey)
      .containsOnly(definitionKey1);
    assertThat(getAllDocumentsOfIndexAs(getDecisionInstanceIndexAliasName(definitionKey2), DecisionInstanceDto.class))
      .singleElement()
      .extracting(DecisionInstanceDto::getDecisionDefinitionKey)
      .isEqualTo(definitionKey2);

    // the multi alias points to all new instance indices
    assertThat(getAllDocumentsOfIndex(DECISION_INSTANCE_MULTI_ALIAS)).hasSize(3);
  }
}
