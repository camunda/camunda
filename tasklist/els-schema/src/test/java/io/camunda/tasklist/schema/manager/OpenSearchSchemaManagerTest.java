/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.manager;

import static io.camunda.tasklist.property.TasklistProperties.OPEN_SEARCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.os.RetryOpenSearchClient;
import io.camunda.tasklist.property.TasklistOpenSearchProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.PutIndexTemplateRequest;

@ExtendWith(MockitoExtension.class)
class OpenSearchSchemaManagerTest {

  @InjectMocks private OpenSearchSchemaManager openSearchSchemaManager;

  @Mock private TasklistProperties tasklistProperties;

  @Mock private TasklistOpenSearchProperties openSearchProperties;

  @Mock private RetryOpenSearchClient retryOpenSearchClient;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private OpenSearchClient openSearchClient;

  @Spy @InjectMocks private TaskTemplate taskTemplate = new TaskTemplate();

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  public void setUp() {
    when(tasklistProperties.getOpenSearch()).thenReturn(openSearchProperties);
    when(tasklistProperties.getDatabase()).thenReturn(OPEN_SEARCH);
    when(openSearchClient._transport().jsonpMapper()).thenReturn(new JacksonJsonpMapper());
  }

  @Test
  void createTemplateShouldCreateIndexWithSettingsAndAliasAndMappings() throws IOException {
    // given
    when(tasklistProperties.getOpenSearch().getNumberOfShards()).thenReturn(3);
    when(tasklistProperties.getOpenSearch().getNumberOfReplicas()).thenReturn(2);
    when(tasklistProperties.getOpenSearch().getIndexPrefix()).thenReturn("prefix");

    // when
    openSearchSchemaManager.createTemplate(taskTemplate);

    // then
    final ArgumentCaptor<CreateIndexRequest> requestCaptor =
        ArgumentCaptor.forClass(CreateIndexRequest.class);
    verify(retryOpenSearchClient).createIndex(requestCaptor.capture());
    final CreateIndexRequest request = requestCaptor.getValue();
    assertThat(request.settings().numberOfShards()).isEqualTo("3");
    assertThat(request.settings().numberOfReplicas()).isEqualTo("2");
    assertThat(request.aliases().keySet().iterator().next()).isEqualTo("prefix-task-8.5.0_alias");
    validateMappings(request.mappings(), taskTemplate.getSchemaClasspathFilename());
  }

  @ParameterizedTest
  @ValueSource(ints = {100})
  @NullSource
  void shouldSetIndexTemplatePriority(final Integer priority) {
    // given
    when(openSearchProperties.getIndexTemplatePriority()).thenReturn(priority);

    // when
    openSearchSchemaManager.createTemplate(taskTemplate);

    // then
    final var requestCaptor = ArgumentCaptor.forClass(PutIndexTemplateRequest.class);
    verify(retryOpenSearchClient).createTemplate(requestCaptor.capture(), eq(false));
    final var request = requestCaptor.getValue();
    assertThat(request.priority()).isEqualTo(priority);
  }

  private void validateMappings(final TypeMapping mapping, final String fileName)
      throws IOException {

    final var propertiesMap = getFileProperties(fileName);

    assertThat(mapping.properties().size()).isEqualTo(propertiesMap.size());
    propertiesMap.forEach(
        (key, value) ->
            assertThat(mapping.properties().get(key)._kind().jsonValue())
                .isEqualTo(value.get("type")));
  }

  private Map<String, Map<String, Object>> getFileProperties(final String fileName)
      throws IOException {
    try (final var expectedMappings =
        OpenSearchSchemaManagerTest.class.getResourceAsStream(fileName)) {
      final var jsonMap =
          objectMapper.readValue(expectedMappings, new TypeReference<Map<String, Object>>() {});
      return (Map<String, Map<String, Object>>) jsonMap.get("properties");
    }
  }
}
