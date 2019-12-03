/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.from26To27;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.collection.BaseCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom26To27;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COLLECTION_INDEX_NAME;

public class UpgradeCollectionScopeIT extends AbstractUpgradeIT {
  private static final String FROM_VERSION = "2.6.0";
  private static final String EXISTING_COLLECTION_ID_1 = "89f9000b-1ab6-42b0-aa49-8f02bbd95309";
  private static final String EXISTING_COLLECTION_ID_2 = "a0b860f2-d07e-4779-97b3-320b4e0f20ee";
  private static final String EXISTING_COLLECTION_ID_3 = "596d9679-894f-4fb9-a646-2220af776d34";
  private static final Set<String> EXISTING_COLLECTION_IDS = ImmutableSet.of(
    EXISTING_COLLECTION_ID_1, EXISTING_COLLECTION_ID_2, EXISTING_COLLECTION_ID_3
  );

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    for (StrictIndexMappingCreator index : ALL_INDICES) {
      createOptimizeIndexWithTypeAndVersion(
        index,
        index.getIndexName(),
        index.getVersion() - 1
      );
    }
    setMetadataIndexVersionWithType(FROM_VERSION, METADATA_INDEX.getIndexName());

    executeBulk("steps/collection/26-collection-bulk");
    executeBulk("steps/report_data/26-single-decision-report-bulk");
    executeBulk("steps/report_data/26-single-process-report-bulk");
    executeBulk("steps/report_data/26-single-process-report-of-multi-report-collection-bulk");
    executeBulk("steps/report_data/26-single-decision-report-of-multi-report-collection-bulk");
    executeBulk("steps/alert_data/26-alert-bulk");
  }

  @Test
  public void scopeIsAddedToExistingCollection1() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom26To27().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final Optional<CollectionDefinitionDto> collectionDefinitionDto = getCollectionById(EXISTING_COLLECTION_ID_1);
    assertThat(collectionDefinitionDto)
      .get()
      .extracting(BaseCollectionDefinitionDto::getData)
      .extracting(CollectionDataDto::getScope)
      .satisfies(collectionScopeEntryDtos -> {
        assertThat(collectionScopeEntryDtos)
          .extracting(
            CollectionScopeEntryDto::getId,
            CollectionScopeEntryDto::getDefinitionType,
            CollectionScopeEntryDto::getDefinitionKey,
            CollectionScopeEntryDto::getTenants
          )
          .containsExactlyInAnyOrder(
            new Tuple(
              "process:invoice",
              DefinitionType.PROCESS,
              "invoice",
              Lists.newArrayList(null, "tenant1")
            ),
            new Tuple(
              "decision:invoiceClassification",
              DefinitionType.DECISION,
              "invoiceClassification",
              Lists.newArrayList((Object) null)
            )
          );
      });
  }

  @Test
  public void scopeIsAddedToExistingCollection2_multiReportScopeOverlapIsMerged() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom26To27().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final Optional<CollectionDefinitionDto> collectionDefinitionDto = getCollectionById(EXISTING_COLLECTION_ID_2);
    assertThat(collectionDefinitionDto)
      .get()
      .extracting(BaseCollectionDefinitionDto::getData)
      .extracting(CollectionDataDto::getScope)
      .satisfies(collectionScopeEntryDtos -> {
        assertThat(collectionScopeEntryDtos)
          .extracting(
            CollectionScopeEntryDto::getId,
            CollectionScopeEntryDto::getDefinitionType,
            CollectionScopeEntryDto::getDefinitionKey,
            scopeEntryDto -> {
              // for easier testing sort, there is no specific order expected to be stored
              scopeEntryDto.getTenants().sort(Comparator.nullsFirst(naturalOrder()));
              return scopeEntryDto.getTenants();
            }
          )
          .containsExactlyInAnyOrder(
            new Tuple(
              "process:invoice",
              DefinitionType.PROCESS,
              "invoice",
              // tenants of multiple reports for same type and key are expected to be merged
              Lists.newArrayList(null, "tenant1", "tenant2", "tenant3")
            ),
            new Tuple(
              "decision:invoiceClassification",
              DefinitionType.DECISION,
              "invoiceClassification",
              // tenants of multiple reports for same type and key are expected to be merged
              Lists.newArrayList("tenant1", "tenant4")
            )
          );
      });
  }

  @Test
  public void stillEmptyScopeOnEmptyCollection3() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom26To27().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final Optional<CollectionDefinitionDto> collectionDefinitionDto = getCollectionById(EXISTING_COLLECTION_ID_3);
    assertThat(collectionDefinitionDto)
      .get()
      .extracting(BaseCollectionDefinitionDto::getData)
      .extracting(CollectionDataDto::getScope)
      .asList()
      .hasSize(0);
  }

  @Test
  public void scopeIsAddedToGeneratedAlertArchiveCollections() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom26To27().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final List<CollectionDefinitionDto> addedCollections = getAllCollections()
      .stream()
      .filter(simpleCollectionDefinitionDto -> !EXISTING_COLLECTION_IDS.contains(simpleCollectionDefinitionDto.getId()))
      .collect(toList());

    assertThat(addedCollections)
      .hasOnlyOneElementSatisfying(simpleCollectionDefinitionDto -> {
        assertThat(simpleCollectionDefinitionDto)
          .extracting(BaseCollectionDefinitionDto::getData)
          .extracting(CollectionDataDto::getScope)
          .satisfies(collectionScopeEntryDtos -> {
            assertThat(collectionScopeEntryDtos)
              .extracting(
                CollectionScopeEntryDto::getId,
                CollectionScopeEntryDto::getDefinitionType,
                CollectionScopeEntryDto::getDefinitionKey,
                CollectionScopeEntryDto::getTenants
              )
              .containsExactlyInAnyOrder(
                new Tuple(
                  "process:invoice",
                  DefinitionType.PROCESS,
                  "invoice",
                  Lists.newArrayList((Object) null)
                ),
                new Tuple(
                  "decision:invoiceClassification",
                  DefinitionType.DECISION,
                  "invoiceClassification",
                  Lists.newArrayList((Object) null)
                )
              );
          });
      });
  }

  @SneakyThrows
  private Optional<CollectionDefinitionDto> getCollectionById(final String s) {
    final GetResponse getResponse = prefixAwareClient.get(
      new GetRequest(COLLECTION_INDEX_NAME).id(s), RequestOptions.DEFAULT
    );
    if (getResponse.isExists()) {
      return Optional.ofNullable(
        objectMapper.readValue(getResponse.getSourceAsString(), CollectionDefinitionDto.class)
      );
    } else {
      return Optional.empty();
    }
  }

  @SneakyThrows
  private List<CollectionDefinitionDto> getAllCollections() {
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(COLLECTION_INDEX_NAME).source(new SearchSourceBuilder().size(10000)),
      RequestOptions.DEFAULT
    );
    return Arrays
      .stream(searchResponse.getHits().getHits())
      .map(doc -> {
        try {
          return objectMapper.readValue(doc.getSourceAsString(), CollectionDefinitionDto.class);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      })
      .collect(toList());
  }

}
