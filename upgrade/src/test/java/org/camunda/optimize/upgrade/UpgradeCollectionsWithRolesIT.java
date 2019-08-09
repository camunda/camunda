/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade;

import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRole;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.service.es.schema.type.CollectionType;
import org.camunda.optimize.service.es.schema.type.DecisionDefinitionType;
import org.camunda.optimize.service.es.schema.type.report.SingleDecisionReportType;
import org.camunda.optimize.service.es.schema.type.report.SingleProcessReportType;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom25To26;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class UpgradeCollectionsWithRolesIT extends AbstractUpgradeIT {
  private static final String FROM_VERSION = "2.5.0";

  private static final DecisionDefinitionType DECISION_DEFINITION_TYPE_OBJECT = new DecisionDefinitionType();
  private static final SingleDecisionReportType SINGLE_DECISION_REPORT_TYPE = new SingleDecisionReportType();
  private static final SingleProcessReportType SINGLE_PROCESS_REPORT_TYPE = new SingleProcessReportType();
  private static final CollectionType COLLECTION_TYPE = new CollectionType();

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(
      METADATA_TYPE,
      DECISION_DEFINITION_TYPE_OBJECT,
      SINGLE_DECISION_REPORT_TYPE,
      SINGLE_PROCESS_REPORT_TYPE,
      COLLECTION_TYPE
    ));

    setMetadataIndexVersion(FROM_VERSION);

    executeBulk("steps/collection/25-collection-bulk");
  }

  @Test
  public void collectionsHaveOwnerAsManagerRole() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    List<SimpleCollectionDefinitionDto> allCollections = getAllCollections();
    assertThat(allCollections.size(), is(1));
    assertThat(allCollections.get(0).getData().getRoles().size(), is(1));
    CollectionRoleDto roleEntry = allCollections.get(0).getData().getRoles().get(0);
    assertThat(roleEntry.getId(), is(IdentityType.USER.name() + ":demo"));
    assertThat(roleEntry.getIdentity().getType(), is(IdentityType.USER));
    assertThat(roleEntry.getIdentity().getId(), is("demo"));
    assertThat(roleEntry.getRole(), is(CollectionRole.MANAGER));
  }


  @SneakyThrows
  private List<SimpleCollectionDefinitionDto> getAllCollections() {
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(COLLECTION_TYPE.getType()).source(new SearchSourceBuilder().size(10000)),
      RequestOptions.DEFAULT
    );
    return Arrays
      .stream(searchResponse.getHits().getHits())
      .map(doc -> {
        try {
          return objectMapper.readValue(
            doc.getSourceAsString(), SimpleCollectionDefinitionDto.class
          );
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      })
      .collect(Collectors.toList());
  }

}