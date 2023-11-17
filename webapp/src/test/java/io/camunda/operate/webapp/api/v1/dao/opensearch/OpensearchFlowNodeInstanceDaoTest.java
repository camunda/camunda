/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OpensearchFlowNodeInstanceDaoTest {

  @Mock
  private OpensearchQueryDSLWrapper mockQueryWrapper;

  @Mock
  private OpensearchRequestDSLWrapper mockRequestWrapper;

  @Mock
  private RichOpenSearchClient mockOpensearchClient;

  @Mock
  private FlowNodeInstanceTemplate mockFlowNodeIndex;

  @Mock
  private ProcessCache mockProcessCache;

  private OpensearchFlowNodeInstanceDao underTest;

  @BeforeEach
  public void setup() {
    underTest = new OpensearchFlowNodeInstanceDao(mockQueryWrapper, mockRequestWrapper, mockFlowNodeIndex, mockOpensearchClient, mockProcessCache);
  }

  @Test
  public void testGetKeyFieldName() {
    assertThat(underTest.getKeyFieldName()).isEqualTo(FlowNodeInstance.KEY);
  }

  @Test
  public void testGetByKeyServerReadErrorMessage() {
    assertThat(underTest.getByKeyServerReadErrorMessage(1L)).isEqualTo("Error in reading flownode instance for key 1");
  }

  @Test
  public void testGetByKeyNoResultsErrorMessage() {
    assertThat(underTest.getByKeyNoResultsErrorMessage(1L)).isEqualTo("No flownode instance found for key 1");
  }

  @Test
  public void testGetByKeyTooManyResultsErrorMessage() {
    assertThat(underTest.getByKeyTooManyResultsErrorMessage(1L)).isEqualTo("Found more than one flownode instances for key 1");
  }

  @Test
  public void testGetIndexName() {
    when(mockFlowNodeIndex.getAlias()).thenReturn("flowNodeIndex");
    assertThat(underTest.getIndexName()).isEqualTo("flowNodeIndex");
    verify(mockFlowNodeIndex, times(1)).getAlias();
  }

  @Test
  public void testGetUniqueSortKey() {
    assertThat(underTest.getUniqueSortKey()).isEqualTo(FlowNodeInstance.KEY);
  }

  @Test
  public void testGetModelClass() {
    assertThat(underTest.getModelClass()).isEqualTo(FlowNodeInstance.class);
  }

  @Test
  public void testBuildFilteringWithNullFilter() {
    SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    underTest.buildFiltering(new Query<>(), mockSearchRequest);

    // Verify that the query was not modified in any way
    verifyNoInteractions(mockSearchRequest);
    verifyNoInteractions(mockQueryWrapper);
  }

  @Test
  public void testBuildFilteringWithAllNullFilterFields() {
    SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    Query<FlowNodeInstance> inputQuery = new Query<FlowNodeInstance>().setFilter(new FlowNodeInstance());

    underTest.buildFiltering(inputQuery, mockSearchRequest);

    // Verify that the query was not modified in any way
    verifyNoInteractions(mockSearchRequest);
    verifyNoInteractions(mockQueryWrapper);
  }

  @Test
  public void testBuildFilteringWithValidFields() {
    SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    FlowNodeInstance filter = new FlowNodeInstance().setKey(1L).setProcessInstanceKey(2L).setProcessDefinitionKey(3L)
        .setStartDate("01-01-2020").setEndDate("01-02-2020").setState("state").setType("type").setFlowNodeId("nodeA")
        .setIncident(true).setIncidentKey(4L).setTenantId("tenant");

    Query<FlowNodeInstance> inputQuery = new Query<FlowNodeInstance>().setFilter(filter);

    underTest.buildFiltering(inputQuery, mockSearchRequest);

    // Verify that each field from the flow node filter was added as a query term to the query
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.KEY, filter.getKey());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.PROCESS_DEFINITION_KEY, filter.getProcessDefinitionKey());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.START_DATE, filter.getStartDate());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.END_DATE, filter.getEndDate());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.STATE, filter.getState());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.TYPE, filter.getType());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.FLOW_NODE_ID, filter.getFlowNodeId());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.INCIDENT, filter.getIncident());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.INCIDENT_KEY, filter.getIncidentKey());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.TENANT_ID, filter.getTenantId());
  }

  @Test
  public void testBuildFilteringIgnoresFlowNodeName() {
    SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    FlowNodeInstance filter = new FlowNodeInstance().setFlowNodeName("name");

    Query<FlowNodeInstance> inputQuery = new Query<FlowNodeInstance>().setFilter(filter);

    underTest.buildFiltering(inputQuery, mockSearchRequest);

    // Verify that the query was not modified in any way
    verifyNoInteractions(mockSearchRequest);
    verifyNoInteractions(mockQueryWrapper);
  }

  @Test
  public void testTransformHitToItemWithValidItem() {
    FlowNodeInstance item = new FlowNodeInstance().setProcessDefinitionKey(1L).setFlowNodeId("id");

    Hit<FlowNodeInstance> mockHit = Mockito.mock(Hit.class);
    when(mockHit.source()).thenReturn(item);

    when(mockProcessCache.getFlowNodeNameOrDefaultValue(item.getProcessDefinitionKey(),
        item.getFlowNodeId(), null)).thenReturn("name");

    FlowNodeInstance result = underTest.transformHitToItem(mockHit);

    assertThat(result.getFlowNodeName()).isEqualTo("name");
    verify(mockProcessCache, times(1)).getFlowNodeNameOrDefaultValue(item.getProcessDefinitionKey(),
        item.getFlowNodeId(), null);
  }

  @Test
  public void testTransformHitToItemWithNoId() {
    FlowNodeInstance item = new FlowNodeInstance().setProcessDefinitionKey(1L);

    Hit<FlowNodeInstance> mockHit = Mockito.mock(Hit.class);
    when(mockHit.source()).thenReturn(item);

    FlowNodeInstance result = underTest.transformHitToItem(mockHit);

    assertThat(result).isSameAs(item);
    verifyNoInteractions(mockProcessCache);
  }

  @Test
  public void testTransformHitToItemWithNoNullHit() {
    Hit<FlowNodeInstance> mockHit = Mockito.mock(Hit.class);
    when(mockHit.source()).thenReturn(null);

    FlowNodeInstance result = underTest.transformHitToItem(mockHit);

    assertThat(result).isNull();
    verifyNoInteractions(mockProcessCache);
  }

  @Test
  public void testSearchByKey() {
    SearchRequest.Builder mockRequestBuilder = Mockito.mock(SearchRequest.Builder.class);
    org.opensearch.client.opensearch._types.query_dsl.Query mockOsQuery =
        Mockito.mock(org.opensearch.client.opensearch._types.query_dsl.Query.class);

    when(mockRequestWrapper.searchRequestBuilder(underTest.getIndexName())).thenReturn(mockRequestBuilder);
    when(mockQueryWrapper.withTenantCheck(any())).thenReturn(mockOsQuery);
    when(mockRequestBuilder.query(mockOsQuery)).thenReturn(mockRequestBuilder);

    OpenSearchDocumentOperations mockDoc = Mockito.mock(OpenSearchDocumentOperations.class);
    when(mockOpensearchClient.doc()).thenReturn(mockDoc);

    List<FlowNodeInstance> validResults = Collections.singletonList(new FlowNodeInstance().setProcessDefinitionKey(1L).setFlowNodeId("id"));
    when(mockDoc.searchValues(mockRequestBuilder, FlowNodeInstance.class)).thenReturn(validResults);

    when(mockProcessCache.getFlowNodeNameOrDefaultValue(1L, "id", null)).thenReturn("name");

    List<FlowNodeInstance> results = underTest.searchByKey(1L);

    // Verify the request was built with a tenant check, the index name, and permissive matching
    assertThat(results).isSameAs(validResults);
    assertThat(results.get(0).getFlowNodeName()).isEqualTo("name");
    verify(mockQueryWrapper, times(1)).term(underTest.getKeyFieldName(), 1L);
    verify(mockQueryWrapper, times(1)).withTenantCheck(any());
    verify(mockRequestWrapper, times(1)).searchRequestBuilder(underTest.getIndexName());
  }
}
