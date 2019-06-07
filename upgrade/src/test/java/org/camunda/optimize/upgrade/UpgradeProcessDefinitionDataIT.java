/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade;

import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionType;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom24To25;
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

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class UpgradeProcessDefinitionDataIT extends AbstractUpgradeIT {
  private static final String FROM_VERSION = "2.4.0";

  private static final ProcessDefinitionType PROCESS_DEFINITION_TYPE = new ProcessDefinitionType();

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(METADATA_TYPE, PROCESS_DEFINITION_TYPE));

    setMetadataIndexVersion(FROM_VERSION);

    executeBulk("steps/process_definition/24-process-definition-bulk");
  }

  @Test
  public void processDefinitionHasExpectedUserTaskNames() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom24To25().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final ProcessDefinitionOptimizeDto processDefinitionById = getProcessDefinitionById(
      "aProcess:1:abaa00de-885a-11e9-ab5b-0242ac120003"
    );

    assertThat(processDefinitionById.getUserTaskNames(), is(notNullValue()));
    assertThat(processDefinitionById.getUserTaskNames().size(), is(1));
    assertThat(processDefinitionById.getUserTaskNames().get("userTask"), is("my task"));
  }

  @Test
  public void allProcessDefinitionsHaveUserTaskNames() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom24To25().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    getAllProcessDefinitions().forEach(definition -> assertThat(definition.getUserTaskNames().size(), is(1)));
  }


  @SneakyThrows
  private ProcessDefinitionOptimizeDto getProcessDefinitionById(final String id) {
    final GetResponse reportResponse = getProcessDefinition(id);
    return objectMapper.readValue(
      reportResponse.getSourceAsString(), ProcessDefinitionOptimizeDto.class
    );
  }

  @SneakyThrows
  private GetResponse getProcessDefinition(final String id) {
    return restClient.get(
      new GetRequest(getProcessDefinitionIndexAlias(), PROCESS_DEFINITION_TYPE.getType(), id), RequestOptions.DEFAULT
    );
  }

  @SneakyThrows
  private List<ProcessDefinitionOptimizeDto> getAllProcessDefinitions() {
    final SearchResponse searchResponse = restClient.search(
      new SearchRequest(getProcessDefinitionIndexAlias()).source(new SearchSourceBuilder().size(10000)),
      RequestOptions.DEFAULT
    );
    return Arrays
      .stream(searchResponse.getHits().getHits())
      .map(doc -> {
        try {
          return objectMapper.readValue(
            doc.getSourceAsString(), ProcessDefinitionOptimizeDto.class
          );
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      })
      .collect(Collectors.toList());
  }

  private String getProcessDefinitionIndexAlias() {
    return getOptimizeIndexAliasForType(PROCESS_DEFINITION_TYPE.getType());
  }
}
