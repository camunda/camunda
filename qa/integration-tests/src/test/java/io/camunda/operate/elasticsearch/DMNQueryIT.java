/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.elasticsearch;

import static io.camunda.operate.util.TestUtil.createDecisionInstanceEntity;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.entities.dmn.DecisionInstanceEntity;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.util.OperateAbstractIT;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DMNQueryIT extends OperateAbstractIT {

  @Rule
  public SearchTestRule searchTestRule = new SearchTestRule();

  @Autowired
  private DecisionInstanceTemplate decisionInstanceTemplate;

  @Autowired
  private TestSearchRepository testSearchRepository;

  @Test
  public void testReadWriteDecisions() throws Exception {
    createData();

    final List<DecisionInstanceEntity> decisionInstances = testSearchRepository.searchAll(decisionInstanceTemplate.getFullQualifiedName(), DecisionInstanceEntity.class);

    assertThat(decisionInstances).hasSize(2);
    assertThat(decisionInstances.get(0).getEvaluatedInputs()).hasSize(2);
    assertThat(decisionInstances.get(0).getEvaluatedOutputs()).hasSize(2);
  }

  protected void createData() {
    searchTestRule.persistNew(createDecisionInstanceEntity(), createDecisionInstanceEntity());
  }

}
