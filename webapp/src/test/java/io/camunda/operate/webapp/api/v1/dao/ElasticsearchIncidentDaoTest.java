/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import io.camunda.operate.property.OperateElasticsearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.webapp.api.v1.entities.Incident;
import io.camunda.operate.webapp.api.v1.entities.Query;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ElasticsearchIncidentDaoTest {

    @Mock
    private IncidentTemplate mockIncidentIndex;

    @Mock
    private OperateProperties mockOperateProperties;

    @Mock
    private OperateElasticsearchProperties mockElasticsearchProperties;

    @InjectMocks
    private ElasticsearchIncidentDao underTest;

    @Captor
    private ArgumentCaptor<QueryBuilder> queryCaptor;

    @Test
    public void testBuildFilteringWithIncidentFilter() {
        Incident testFilter = new Incident();
        testFilter.setKey(123L).setProcessDefinitionKey(222L).setProcessInstanceKey(333L).setType("type")
                .setMessage("message").setState("state").setJobKey(444L).setTenantId("fakeTenant")
                .setCreationTime("01-01-2020");

        SearchSourceBuilder mockBuilder = Mockito.mock(SearchSourceBuilder.class);
        Query<Incident> mockQuery = Mockito.mock(Query.class);

        when(mockQuery.getFilter()).thenReturn(testFilter);
        when(mockOperateProperties.getElasticsearch()).thenReturn(mockElasticsearchProperties);
        when(mockElasticsearchProperties.getDateFormat()).thenReturn("yyyy.MM.dd");

        underTest.buildFiltering(mockQuery, mockBuilder);

        // Capture the queryBuilder object
        verify(mockBuilder).query(queryCaptor.capture());
        QueryBuilder capturedArgument = queryCaptor.getValue();
        assertThat(capturedArgument instanceof BoolQueryBuilder).isTrue();

        // Check that 9 filters are present
        List<QueryBuilder> mustClauses = ((BoolQueryBuilder)capturedArgument).must();
        assertThat(mustClauses.size()).isEqualTo(9);

        // Check the validity of each filter
        assertThat(((TermQueryBuilder)(mustClauses.get(0))).fieldName()).isEqualTo(Incident.KEY);
        assertThat(((TermQueryBuilder)(mustClauses.get(0))).value()).isEqualTo(testFilter.getKey());
        assertThat(((TermQueryBuilder)(mustClauses.get(1))).fieldName()).isEqualTo(Incident.PROCESS_DEFINITION_KEY);
        assertThat(((TermQueryBuilder)(mustClauses.get(1))).value()).isEqualTo(testFilter.getProcessDefinitionKey());
        assertThat(((TermQueryBuilder)(mustClauses.get(2))).fieldName()).isEqualTo(Incident.PROCESS_INSTANCE_KEY);
        assertThat(((TermQueryBuilder)(mustClauses.get(2))).value()).isEqualTo(testFilter.getProcessInstanceKey());
        assertThat(((TermQueryBuilder)(mustClauses.get(3))).fieldName()).isEqualTo(Incident.TYPE);
        assertThat(((TermQueryBuilder)(mustClauses.get(3))).value()).isEqualTo(testFilter.getType());
        assertThat(((MatchQueryBuilder)(mustClauses.get(4))).fieldName()).isEqualTo(Incident.MESSAGE);
        assertThat(((MatchQueryBuilder)(mustClauses.get(4))).value()).isEqualTo(testFilter.getMessage());
        assertThat(((TermQueryBuilder)(mustClauses.get(5))).fieldName()).isEqualTo(Incident.STATE);
        assertThat(((TermQueryBuilder)(mustClauses.get(5))).value()).isEqualTo(testFilter.getState());
        assertThat(((TermQueryBuilder)(mustClauses.get(6))).fieldName()).isEqualTo(Incident.JOB_KEY);
        assertThat(((TermQueryBuilder)(mustClauses.get(6))).value()).isEqualTo(testFilter.getJobKey());
        assertThat(((TermQueryBuilder)(mustClauses.get(7))).fieldName()).isEqualTo(Incident.TENANT_ID);
        assertThat(((TermQueryBuilder)(mustClauses.get(7))).value()).isEqualTo(testFilter.getTenantId());
        assertThat(((RangeQueryBuilder)(mustClauses.get(8))).fieldName()).isEqualTo(Incident.CREATION_TIME);
        assertThat(((RangeQueryBuilder)(mustClauses.get(8))).format()).isEqualTo("yyyy.MM.dd");
        assertThat(((RangeQueryBuilder)(mustClauses.get(8))).from()).isEqualTo(testFilter.getCreationTime());
    }
}
