/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import io.camunda.operate.webapp.writer.ProcessInstanceWriter;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.core.SearchRequest;

@ExtendWith(MockitoExtension.class)
public class OpensearchProcessInstanceDaoTest {

  @Mock private OpensearchQueryDSLWrapper mockQueryWrapper;

  @Mock private OpensearchRequestDSLWrapper mockRequestWrapper;

  @Mock private RichOpenSearchClient mockOpensearchClient;

  @Mock private ProcessInstanceWriter mockProcessInstanceWriter;

  @Mock private ListViewTemplate mockProcessInstanceIndex;

  @Mock private OperateDateTimeFormatter mockDateTimeFormatter;

  private OpensearchProcessInstanceDao underTest;

  @BeforeEach
  public void setup() {
    underTest =
        new OpensearchProcessInstanceDao(
            mockQueryWrapper,
            mockRequestWrapper,
            mockOpensearchClient,
            mockProcessInstanceIndex,
            mockProcessInstanceWriter,
            mockDateTimeFormatter);
  }

  @Test
  public void testGetUniqueSortKey() {
    assertThat(underTest.getUniqueSortKey()).isEqualTo(ListViewTemplate.KEY);
  }

  @Test
  public void testGetKeyFieldName() {
    assertThat(underTest.getKeyFieldName()).isEqualTo(ProcessInstance.KEY);
  }

  @Test
  public void testGetInternalDocumentModelClass() {
    assertThat(underTest.getInternalDocumentModelClass()).isEqualTo(ProcessInstance.class);
  }

  @Test
  public void testGetIndexName() {
    when(mockProcessInstanceIndex.getAlias()).thenReturn("processInstanceIndex");
    assertThat(underTest.getIndexName()).isEqualTo("processInstanceIndex");
    verify(mockProcessInstanceIndex, times(1)).getAlias();
  }

  @Test
  public void testGetByKeyServerReadErrorMessage() {
    assertThat(underTest.getByKeyServerReadErrorMessage(1L))
        .isEqualTo("Error in reading process instance for key 1");
  }

  @Test
  public void testGetByKeyNoResultsErrorMessage() {
    assertThat(underTest.getByKeyNoResultsErrorMessage(1L))
        .isEqualTo("No process instances found for key 1");
  }

  @Test
  public void testGetByKeyTooManyResultsErrorMessage() {
    assertThat(underTest.getByKeyTooManyResultsErrorMessage(1L))
        .isEqualTo("Found more than one process instances for key 1");
  }

  @Test
  public void testSearchByKey() {
    final SearchRequest.Builder mockRequestBuilder = Mockito.mock(SearchRequest.Builder.class);
    final org.opensearch.client.opensearch._types.query_dsl.Query mockOsQuery =
        Mockito.mock(org.opensearch.client.opensearch._types.query_dsl.Query.class);

    when(mockRequestWrapper.searchRequestBuilder(underTest.getIndexName()))
        .thenReturn(mockRequestBuilder);
    when(mockQueryWrapper.withTenantCheck(any())).thenReturn(mockOsQuery);
    when(mockRequestBuilder.query(mockOsQuery)).thenReturn(mockRequestBuilder);

    final OpenSearchDocumentOperations mockDoc = Mockito.mock(OpenSearchDocumentOperations.class);
    when(mockOpensearchClient.doc()).thenReturn(mockDoc);

    final List<ProcessInstance> validResults = Collections.singletonList(new ProcessInstance());
    when(mockDoc.searchValues(mockRequestBuilder, ProcessInstance.class)).thenReturn(validResults);

    final List<ProcessInstance> results = underTest.searchByKey(1L);

    // Verify the request was built with a tenant check, the index name, and permissive matching
    assertThat(results).isSameAs(validResults);
    verify(mockQueryWrapper, times(1)).term(underTest.getKeyFieldName(), 1L);
    verify(mockQueryWrapper, times(1))
        .term(ListViewTemplate.JOIN_RELATION, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION);
    verify(mockQueryWrapper, times(1)).withTenantCheck(any());
    verify(mockRequestWrapper, times(1)).searchRequestBuilder(underTest.getIndexName());
  }

  @Test
  public void testBuildFilteringWithNullFilter() {
    final SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    underTest.buildFiltering(new Query<>(), mockSearchRequest);

    // Verify that the join relation was still set
    verify(mockQueryWrapper, times(1))
        .term(ListViewTemplate.JOIN_RELATION, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION);
  }

  @Test
  public void testBuildFilteringWithAllNullFilterFields() {
    final SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    final Query<ProcessInstance> inputQuery =
        new Query<ProcessInstance>().setFilter(new ProcessInstance());

    underTest.buildFiltering(inputQuery, mockSearchRequest);

    // Verify that the join relation was still set
    verify(mockQueryWrapper, times(1))
        .term(ListViewTemplate.JOIN_RELATION, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION);
  }

  @Test
  public void testBuildFilteringWithValidFields() {
    final SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    final ProcessInstance filter =
        new ProcessInstance()
            .setKey(1L)
            .setProcessDefinitionKey(2L)
            .setParentKey(3L)
            .setParentFlowNodeInstanceKey(4L)
            .setProcessVersion(1)
            .setBpmnProcessId("bpmnId")
            .setState("state")
            .setIncident(false)
            .setTenantId("tenant")
            .setStartDate("2024-01-19T18:39:05.196-0500")
            .setEndDate("2024-01-19T18:39:06.196-0500");

    final String expectedDateFormat = OperateDateTimeFormatter.DATE_FORMAT_DEFAULT;
    when(mockDateTimeFormatter.getApiDateTimeFormatString()).thenReturn(expectedDateFormat);

    final Query<ProcessInstance> inputQuery = new Query<ProcessInstance>().setFilter(filter);

    underTest.buildFiltering(inputQuery, mockSearchRequest);

    // Verify that each field from the process instance filter was added as a query term to the
    // query
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.KEY, filter.getKey());
    verify(mockQueryWrapper, times(1))
        .term(ProcessInstance.PROCESS_DEFINITION_KEY, filter.getProcessDefinitionKey());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.PARENT_KEY, filter.getParentKey());
    verify(mockQueryWrapper, times(1))
        .term(ProcessInstance.PARENT_FLOW_NODE_INSTANCE_KEY, filter.getParentFlowNodeInstanceKey());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.VERSION, filter.getProcessVersion());
    verify(mockQueryWrapper, times(1))
        .term(ProcessInstance.BPMN_PROCESS_ID, filter.getBpmnProcessId());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.STATE, filter.getState());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.INCIDENT, filter.getIncident());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.TENANT_ID, filter.getTenantId());
    verify(mockQueryWrapper, times(1))
        .matchDateQuery(ProcessInstance.START_DATE, filter.getStartDate(), expectedDateFormat);
    verify(mockQueryWrapper, times(1))
        .matchDateQuery(ProcessInstance.END_DATE, filter.getEndDate(), expectedDateFormat);

    // Verify that the join relation was still set
    verify(mockQueryWrapper, times(1))
        .term(ListViewTemplate.JOIN_RELATION, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION);
  }
}
