/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Variable;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.core.SearchRequest;

@ExtendWith(MockitoExtension.class)
public class OpensearchVariableDaoTest {

  @Mock private OpensearchQueryDSLWrapper mockQueryWrapper;

  @Mock private OpensearchRequestDSLWrapper mockRequestWrapper;

  @Mock private VariableTemplate mockVariableIndex;

  @Mock private RichOpenSearchClient mockOpensearchClient;

  private OpensearchVariableDao underTest;

  @BeforeEach
  public void setup() {
    underTest =
        new OpensearchVariableDao(
            mockQueryWrapper, mockRequestWrapper, mockOpensearchClient, mockVariableIndex);
  }

  @Test
  public void testGetIndexName() {
    when(mockVariableIndex.getAlias()).thenReturn("variableIndex");
    assertThat(underTest.getIndexName()).isEqualTo("variableIndex");
    verify(mockVariableIndex, times(1)).getAlias();
  }

  @Test
  public void testGetUniqueSortKey() {
    assertThat(underTest.getUniqueSortKey()).isEqualTo(Variable.KEY);
  }

  @Test
  public void testGetInternalDocumentModelClass() {
    assertThat(underTest.getInternalDocumentModelClass()).isEqualTo(Variable.class);
  }

  @Test
  public void testGetKeyFieldName() {
    assertThat(underTest.getKeyFieldName()).isEqualTo(Variable.KEY);
  }

  @Test
  public void testGetByKeyServerReadErrorMessage() {
    assertThat(underTest.getByKeyServerReadErrorMessage(1L))
        .isEqualTo("Error in reading variable for key 1");
  }

  @Test
  public void testGetByKeyNoResultsErrorMessage() {
    assertThat(underTest.getByKeyNoResultsErrorMessage(1L))
        .isEqualTo("No variable found for key 1");
  }

  @Test
  public void testGetByKeyTooManyResultsErrorMessage() {
    assertThat(underTest.getByKeyTooManyResultsErrorMessage(1L))
        .isEqualTo("Found more than one variables for key 1");
  }

  @Test
  public void testBuildFilteringWithNullFilter() {
    final SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    underTest.buildFiltering(new Query<>(), mockSearchRequest);

    // Verify that the query was not modified in any way
    verifyNoInteractions(mockSearchRequest);
    verifyNoInteractions(mockQueryWrapper);
  }

  @Test
  public void testBuildFilteringWithValidFields() {
    final SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    final Variable filter =
        new Variable()
            .setKey(1L)
            .setName("var")
            .setScopeKey(2L)
            .setProcessInstanceKey(3L)
            .setTruncated(true)
            .setValue("val")
            .setTenantId("tenant");

    final Query<Variable> inputQuery = new Query<Variable>().setFilter(filter);

    underTest.buildFiltering(inputQuery, mockSearchRequest);

    // Verify that each field from the variable was added as a query term to the query
    verify(mockQueryWrapper, times(1)).term(Variable.KEY, filter.getKey());
    verify(mockQueryWrapper, times(1)).term(Variable.TENANT_ID, filter.getTenantId());
    verify(mockQueryWrapper, times(1))
        .term(Variable.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey());
    verify(mockQueryWrapper, times(1)).term(Variable.SCOPE_KEY, filter.getScopeKey());
    verify(mockQueryWrapper, times(1)).term(Variable.NAME, filter.getName());
    verify(mockQueryWrapper, times(1)).term(Variable.VALUE, filter.getValue());
    verify(mockQueryWrapper, times(1)).term(Variable.TRUNCATED, filter.getTruncated());
  }
}
