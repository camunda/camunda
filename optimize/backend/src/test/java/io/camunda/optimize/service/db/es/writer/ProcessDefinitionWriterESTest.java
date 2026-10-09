/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DEFINITION_DELETED;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.ONBOARDED;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.repository.es.TaskRepositoryES;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProcessDefinitionWriterESTest {

  private TaskRepositoryES taskRepositoryES;
  private ProcessDefinitionWriterES writer;

  @BeforeEach
  void setUp() {
    taskRepositoryES = mock(TaskRepositoryES.class);
    writer =
        new ProcessDefinitionWriterES(
            mock(OptimizeElasticsearchClient.class),
            new ObjectMapper(),
            mock(ConfigurationService.class),
            taskRepositoryES);
  }

  @Test
  void shouldOnlyMarkNotYetOnboardedAndNotDeletedVersionsAsOnboardedInBoundedBatches() {
    // given
    final Set<String> keys = Set.of("onboardingKeyA", "onboardingKeyB");

    // when
    writer.markDefinitionKeysAsOnboarded(keys);

    // then
    final ArgumentCaptor<Script> scriptCaptor = ArgumentCaptor.forClass(Script.class);
    final ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(taskRepositoryES)
        .tryUpdateByQueryRequest(
            anyString(),
            scriptCaptor.capture(),
            queryCaptor.capture(),
            eq(50),
            eq(PROCESS_DEFINITION_INDEX_NAME));

    final List<Query> mustClauses = queryCaptor.getValue().bool().must();
    assertThat(mustClauses).hasSize(3);
    assertThat(mustClauses)
        .anySatisfy(
            clause -> {
              assertThat(clause.isTerms()).isTrue();
              assertThat(clause.terms().field()).isEqualTo(PROCESS_DEFINITION_KEY);
              assertThat(clause.terms().terms().value())
                  .extracting(FieldValue::stringValue)
                  .containsExactlyInAnyOrderElementsOf(keys);
            });
    assertThat(mustClauses)
        .anySatisfy(
            clause -> {
              assertThat(clause.isTerm()).isTrue();
              assertThat(clause.term().field()).isEqualTo(ONBOARDED);
              assertThat(clause.term().value().booleanValue()).isFalse();
            });
    assertThat(mustClauses)
        .anySatisfy(
            clause -> {
              assertThat(clause.isTerm()).isTrue();
              assertThat(clause.term().field()).isEqualTo(DEFINITION_DELETED);
              assertThat(clause.term().value().booleanValue()).isFalse();
            });
    assertThat(scriptCaptor.getValue().source()).isEqualTo("ctx._source.onboarded = true");
  }
}
