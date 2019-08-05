/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade;

import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.schema.type.DecisionDefinitionType;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom25To26;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_TYPE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class UpgradeDecisionDefinitionDataIT extends AbstractUpgradeIT {
  private static final String FROM_VERSION = "2.5.0";

  private static final DecisionDefinitionType DECISION_DEFINITION_TYPE_OBJECT = new DecisionDefinitionType();
  private static final String DECISION_DEFINITION_ID = "aDecision:1:dbef181f-af98-11e9-9604-0242ac120003";
  private static final String DECISION_DEFINITION_ID_WITH_MULTIPLE_TABLES =
    "beverages:1:382525ac-b761-11e9-a6ee-0242ac120002";

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(METADATA_TYPE, DECISION_DEFINITION_TYPE_OBJECT));

    setMetadataIndexVersion(FROM_VERSION);

    executeBulk("steps/decision_definition/25-decision-definition-bulk");
  }

  @Test
  public void decisionDefinitionHasExpectedVariableNames() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final DecisionDefinitionOptimizeDto processDefinitionById = getDecisionDefinitionById(
      DECISION_DEFINITION_ID
    );

    assertThat(processDefinitionById.getInputVariableNames(), is(notNullValue()));
    assertThat(processDefinitionById.getInputVariableNames().size(), is(1));
    assertThat(processDefinitionById.getInputVariableNames().get(0).getName(), is("input"));
    assertThat(processDefinitionById.getOutputVariableNames(), is(notNullValue()));
    assertThat(processDefinitionById.getOutputVariableNames().size(), is(1));
    assertThat(processDefinitionById.getOutputVariableNames().get(0).getName(), is("output"));
  }

  @Test
  public void decisionDefinitionWithMultipleTablesHasExpectedVariableNames() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final DecisionDefinitionOptimizeDto processDefinitionById = getDecisionDefinitionById(
      DECISION_DEFINITION_ID_WITH_MULTIPLE_TABLES
    );

    assertThat(processDefinitionById.getInputVariableNames(), is(notNullValue()));
    assertThat(processDefinitionById.getInputVariableNames().size(), is(2));
    assertThat(processDefinitionById.getInputVariableNames().get(0).getName(), is("Dish"));
    assertThat(processDefinitionById.getInputVariableNames().get(1).getName(), is("Guests with children"));
    assertThat(processDefinitionById.getOutputVariableNames(), is(notNullValue()));
    assertThat(processDefinitionById.getOutputVariableNames().size(), is(1));
    assertThat(processDefinitionById.getOutputVariableNames().get(0).getName(), is("Beverages"));
  }

  @SneakyThrows
  private DecisionDefinitionOptimizeDto getDecisionDefinitionById(final String id) {
    final GetResponse reportResponse = getDecisionDefinition(id);
    return objectMapper.readValue(
      reportResponse.getSourceAsString(), DecisionDefinitionOptimizeDto.class
    );
  }

  @SneakyThrows
  private GetResponse getDecisionDefinition(final String id) {
    return prefixAwareClient.get(
      new GetRequest(DECISION_DEFINITION_TYPE, DECISION_DEFINITION_TYPE, id), RequestOptions.DEFAULT
    );
  }

  @SneakyThrows
  private List<DecisionDefinitionOptimizeDto> getAllProcessDefinitions() {
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(DECISION_DEFINITION_TYPE).source(new SearchSourceBuilder().size(10000)),
      RequestOptions.DEFAULT
    );
    return Arrays
      .stream(searchResponse.getHits().getHits())
      .map(doc -> {
        try {
          return objectMapper.readValue(
            doc.getSourceAsString(), DecisionDefinitionOptimizeDto.class
          );
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      })
      .collect(Collectors.toList());
  }

}