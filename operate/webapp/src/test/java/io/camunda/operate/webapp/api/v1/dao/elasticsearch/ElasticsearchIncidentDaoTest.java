/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.webapp.api.v1.entities.Incident;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ElasticsearchIncidentDaoTest {

  private static final JacksonJsonpMapper MAPPER = new JacksonJsonpMapper();

  @Mock private OperateDateTimeFormatter mockDateTimeFormatter;
  @InjectMocks private ElasticsearchIncidentDao underTest;

  @Captor
  private ArgumentCaptor<co.elastic.clients.elasticsearch._types.query_dsl.Query> queryCaptor;

  @Test
  public void testBuildFilteringWithIncidentFilter() {
    final OffsetDateTime creationTime = OffsetDateTime.now();
    final Incident testFilter = new Incident();
    testFilter
        .setKey(123L)
        .setProcessDefinitionKey(222L)
        .setProcessInstanceKey(333L)
        .setType("type")
        .setMessage("message")
        .setState("state")
        .setJobKey(444L)
        .setTenantId("fakeTenant")
        .setCreationTime("2024-02-13T15:10:33.013+0000");

    final var reqBuilder = Mockito.mock(SearchRequest.Builder.class);
    final Query<Incident> mockQuery = Mockito.mock(Query.class);

    when(mockQuery.getFilter()).thenReturn(testFilter);

    underTest.buildFiltering(mockQuery, reqBuilder, false);

    // Capture the queryBuilder object
    verify(reqBuilder).query(queryCaptor.capture());
    final var capturedArgument = queryCaptor.getValue();
    assertThat(capturedArgument instanceof co.elastic.clients.elasticsearch._types.query_dsl.Query)
        .isTrue();

    // Check that 9 filters are present
    final var mustClauses = capturedArgument.bool().must();
    assertThat(mustClauses.size()).isEqualTo(9);

    // Check the validity of each filter
    assertThat(mustClauses.get(0).terms().field()).isEqualTo(Incident.KEY);
    assertThat(unwrapQueryVal(mustClauses.get(0), Long.class)).isEqualTo(testFilter.getKey());

    assertThat(mustClauses.get(1).terms().field()).isEqualTo(Incident.PROCESS_DEFINITION_KEY);
    assertThat(unwrapQueryVal(mustClauses.get(1), Long.class))
        .isEqualTo(testFilter.getProcessDefinitionKey());

    assertThat(mustClauses.get(2).terms().field()).isEqualTo(Incident.PROCESS_INSTANCE_KEY);
    assertThat(unwrapQueryVal(mustClauses.get(2), Long.class))
        .isEqualTo(testFilter.getProcessInstanceKey());

    assertThat(mustClauses.get(3).terms().field()).isEqualTo(Incident.TYPE);
    assertThat(unwrapQueryVal(mustClauses.get(3), String.class)).isEqualTo(testFilter.getType());

    assertThat(mustClauses.get(4).match().field()).isEqualTo(Incident.MESSAGE);
    assertThat(unwrapQueryVal(mustClauses.get(4), String.class)).isEqualTo(testFilter.getMessage());

    assertThat(mustClauses.get(5).terms().field()).isEqualTo(Incident.STATE);
    assertThat(unwrapQueryVal(mustClauses.get(5), String.class)).isEqualTo(testFilter.getState());

    assertThat(mustClauses.get(6).terms().field()).isEqualTo(Incident.JOB_KEY);
    assertThat(unwrapQueryVal(mustClauses.get(6), Long.class)).isEqualTo(testFilter.getJobKey());

    assertThat(mustClauses.get(7).terms().field()).isEqualTo(Incident.TENANT_ID);
    assertThat(unwrapQueryVal(mustClauses.get(7), String.class))
        .isEqualTo(testFilter.getTenantId());

    assertThat(mustClauses.get(8).range().term().field()).isEqualTo(Incident.CREATION_TIME);
    assertThat(mustClauses.get(8).range().term().lte()).isEqualTo("2024-02-13T15:10:33.013+0000");
    assertThat(mustClauses.get(8).range().term().gte()).isEqualTo("2024-02-13T15:10:33.013+0000");
  }

  // only supports necessary types to unwrap for test
  private <T> T unwrapQueryVal(
      final co.elastic.clients.elasticsearch._types.query_dsl.Query query, final Class<T> clazz) {
    if (query.isTerms()) {
      return query.terms().terms().value().get(0).anyValue().to(clazz, MAPPER);
    }

    if (query.isMatch() && query.match().query().isString()) {
      return (T) query.match().query().stringValue();
    }

    throw new IllegalStateException("not supported field value");
  }

  @Test
  public void testFilteringWithNoIncidentFilter() {
    final var reqBuilder = Mockito.mock(SearchRequest.Builder.class);
    final Query<Incident> mockQuery = Mockito.mock(Query.class);

    when(mockQuery.getFilter()).thenReturn(null);

    underTest.buildFiltering(mockQuery, reqBuilder, true);

    // Capture the queryBuilder object
    verify(reqBuilder, times(0))
        .query(any(co.elastic.clients.elasticsearch._types.query_dsl.Query.class));

    verifyNoInteractions(mockDateTimeFormatter);
  }

  @Test
  public void testSearchHitToIncident() {
    final Map<String, Object> searchHitAsMap = new HashMap<>();
    searchHitAsMap.put(IncidentTemplate.KEY, 123L);
    searchHitAsMap.put(IncidentTemplate.PROCESS_INSTANCE_KEY, 222L);
    searchHitAsMap.put(IncidentTemplate.PROCESS_DEFINITION_KEY, 333L);
    searchHitAsMap.put(IncidentTemplate.ERROR_TYPE, "errorType");
    searchHitAsMap.put(IncidentTemplate.ERROR_MSG, "message");
    searchHitAsMap.put(IncidentTemplate.CREATION_TIME, "2024-02-13T15:10:33.013+0000");
    searchHitAsMap.put(IncidentTemplate.STATE, "state");
    searchHitAsMap.put(IncidentTemplate.JOB_KEY, 444L);
    searchHitAsMap.put(IncidentTemplate.TENANT_ID, "tenant");

    final String parsedDateTime = "2024-02-13T15:10:33.013+00:00";
    when(mockDateTimeFormatter.convertGeneralToApiDateTime(anyString())).thenReturn(parsedDateTime);

    final var objectMapper = new ObjectMapper();
    final var result =
        underTest.postProcessIncidents(objectMapper.convertValue(searchHitAsMap, Incident.class));

    assertThat(result).isNotNull();
    assertThat(result.getKey()).isEqualTo(searchHitAsMap.get(IncidentTemplate.KEY));
    assertThat(result.getProcessInstanceKey())
        .isEqualTo(searchHitAsMap.get(IncidentTemplate.PROCESS_INSTANCE_KEY));
    assertThat(result.getProcessDefinitionKey())
        .isEqualTo(searchHitAsMap.get(IncidentTemplate.PROCESS_DEFINITION_KEY));
    assertThat(result.getType()).isEqualTo(searchHitAsMap.get(IncidentTemplate.ERROR_TYPE));
    assertThat(result.getMessage()).isEqualTo(searchHitAsMap.get(IncidentTemplate.ERROR_MSG));
    assertThat(result.getCreationTime()).isEqualTo(parsedDateTime);
    assertThat(result.getState()).isEqualTo(searchHitAsMap.get(IncidentTemplate.STATE));
    assertThat(result.getJobKey()).isEqualTo(searchHitAsMap.get(IncidentTemplate.JOB_KEY));
    assertThat(result.getTenantId()).isEqualTo(searchHitAsMap.get(IncidentTemplate.TENANT_ID));
  }
}
