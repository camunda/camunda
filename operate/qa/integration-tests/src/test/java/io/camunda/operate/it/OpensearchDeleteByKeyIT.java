/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.store.DecisionStore;
import io.camunda.operate.store.ProcessStore;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Time;
import org.springframework.beans.factory.annotation.Autowired;

public class OpensearchDeleteByKeyIT extends OperateSearchAbstractIT {

  private static final Time DEFAULT_REFRESH_INTERVAL = Time.of(t -> t.time("1s"));
  private static final Time REFRESH_DISABLED = Time.of(t -> t.time("-1"));

  @Autowired(required = false)
  private OpenSearchClient operateOpenSearchClient;

  @Autowired private ProcessStore processStore;
  @Autowired private DecisionStore decisionStore;
  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceTemplate;
  @Autowired private DecisionInstanceTemplate decisionInstanceTemplate;

  private String indexWithRefreshDisabled;
  private Time originalRefreshInterval;

  @BeforeEach
  void assumeOpensearch() {
    assumeThat(DatabaseInfo.isOpensearch()).isTrue();
  }

  @AfterEach
  void restoreRefreshInterval() throws IOException {
    if (indexWithRefreshDisabled != null) {
      setRefreshInterval(
          indexWithRefreshDisabled,
          originalRefreshInterval != null ? originalRefreshInterval : DEFAULT_REFRESH_INTERVAL);
      indexWithRefreshDisabled = null;
    }
  }

  Stream<Arguments> deleteByKeyCallers() {
    return Stream.of(
        Arguments.of(
            "ProcessStore#deleteDocument",
            new DeleteByKeyCaller(
                flowNodeInstanceTemplate,
                ProcessInstanceDependant.PROCESS_INSTANCE_KEY,
                key -> new FlowNodeInstanceEntity().setProcessInstanceKey(key),
                processStore::deleteDocument)),
        Arguments.of(
            "DecisionStore#deleteDocuments",
            new DeleteByKeyCaller(
                decisionInstanceTemplate,
                DecisionInstanceTemplate.DECISION_REQUIREMENTS_KEY,
                key -> new DecisionInstanceEntity().setDecisionRequirementsKey(key),
                decisionStore::deleteDocuments)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("deleteByKeyCallers")
  void shouldDeleteRemainingDocumentsWhenOthersWereRemovedAfterTheLastRefresh(
      final String callerName, final DeleteByKeyCaller caller) throws IOException {
    // given
    final long key = ThreadLocalRandom.current().nextLong(1L << 52, 1L << 53);
    final var index = caller.index().getFullQualifiedName();
    final var removedIds =
        List.of(
            testSearchRepository.createOrUpdateDocumentFromObject(
                index, caller.document().apply(key)),
            testSearchRepository.createOrUpdateDocumentFromObject(
                index, caller.document().apply(key)));
    testSearchRepository.createOrUpdateDocumentFromObject(index, caller.document().apply(key));
    searchContainerManager.refreshIndices("*operate*");

    disableRefresh(index);
    for (final var id : removedIds) {
      operateOpenSearchClient.delete(d -> d.index(index).id(id));
    }
    assertThat(testSearchRepository.searchTerm(index, caller.keyField(), key, Object.class, 10))
        .hasSize(removedIds.size() + 1);

    // when
    final long deleted = caller.delete().delete(index, caller.keyField(), String.valueOf(key));

    // then
    assertThat(deleted).isOne();
    searchContainerManager.refreshIndices("*operate*");
    assertThat(testSearchRepository.searchTerm(index, caller.keyField(), key, Object.class, 10))
        .isEmpty();
  }

  private void disableRefresh(final String index) throws IOException {
    originalRefreshInterval =
        operateOpenSearchClient
            .indices()
            .getSettings(g -> g.index(index))
            .result()
            .get(index)
            .settings()
            .index()
            .refreshInterval();
    setRefreshInterval(index, REFRESH_DISABLED);
    indexWithRefreshDisabled = index;
  }

  private void setRefreshInterval(final String index, final Time refreshInterval)
      throws IOException {
    operateOpenSearchClient
        .indices()
        .putSettings(p -> p.index(index).settings(s -> s.refreshInterval(refreshInterval)));
  }

  @FunctionalInterface
  private interface DeleteByKey {
    long delete(String index, String keyField, String key) throws IOException;
  }

  private record DeleteByKeyCaller(
      IndexDescriptor index, String keyField, LongFunction<Object> document, DeleteByKey delete) {}
}
