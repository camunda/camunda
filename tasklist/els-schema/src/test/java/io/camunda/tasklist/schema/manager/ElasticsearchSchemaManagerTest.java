/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.manager;

import static io.camunda.tasklist.property.TasklistProperties.ELASTIC_SEARCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.commons.util.ReflectionUtils.tryToReadFieldValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.property.TasklistElasticsearchProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import org.elasticsearch.client.indices.PutComposableIndexTemplateRequest;
import org.elasticsearch.cluster.metadata.ComposableIndexTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ElasticsearchSchemaManagerTest {

  @InjectMocks private ElasticsearchSchemaManager elasticsearchSchemaManager;

  @Mock private TasklistProperties tasklistProperties;

  @Mock private RetryElasticsearchClient retryElasticsearchClient;

  @Spy @InjectMocks private TaskTemplate taskTemplate = new TaskTemplate();

  @BeforeEach
  public void setUp() {
    when(tasklistProperties.getElasticsearch())
        .thenReturn(mock(TasklistElasticsearchProperties.class));
    when(tasklistProperties.getDatabase()).thenReturn(ELASTIC_SEARCH);
  }

  @ParameterizedTest
  @ValueSource(ints = {100})
  @NullSource
  void shouldSetIndexTemplatePriority(final Integer priority) throws Exception {
    // given
    final var elasticsearchProperties = mock(TasklistElasticsearchProperties.class);
    when(tasklistProperties.getElasticsearch()).thenReturn(elasticsearchProperties);
    when(elasticsearchProperties.getIndexTemplatePriority()).thenReturn(priority);

    // when
    elasticsearchSchemaManager.createTemplate(taskTemplate);

    // then
    final var requestCaptor = ArgumentCaptor.forClass(PutComposableIndexTemplateRequest.class);
    verify(retryElasticsearchClient).createTemplate(requestCaptor.capture());
    final ComposableIndexTemplate indexTemplate =
        (ComposableIndexTemplate)
            tryToReadFieldValue(
                    PutComposableIndexTemplateRequest.class,
                    "indexTemplate",
                    requestCaptor.getValue())
                .get();
    final var expectedPriority = priority != null ? Long.valueOf(priority) : null;
    assertThat(indexTemplate.priority()).isEqualTo(expectedPriority);
  }
}
