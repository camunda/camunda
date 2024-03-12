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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.operate.schema.templates.SequenceFlowTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.SequenceFlow;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.core.SearchRequest;

@ExtendWith(MockitoExtension.class)
public class OpensearchSequenceFlowDaoTest {

  @Mock private OpensearchQueryDSLWrapper mockQueryWrapper;

  @Mock private OpensearchRequestDSLWrapper mockRequestWrapper;

  @Mock private SequenceFlowTemplate mockSequenceFlowIndex;

  @Mock private RichOpenSearchClient mockOpensearchClient;

  private OpensearchSequenceFlowDao underTest;

  @BeforeEach
  public void setup() {
    underTest =
        new OpensearchSequenceFlowDao(
            mockQueryWrapper, mockRequestWrapper, mockOpensearchClient, mockSequenceFlowIndex);
  }

  @Test
  public void testGetSortKey() {
    assertThat(underTest.getUniqueSortKey()).isEqualTo(SequenceFlowTemplate.ID);
  }

  @Test
  public void testGetInternalDocumentModelClass() {
    assertThat(underTest.getInternalDocumentModelClass()).isEqualTo(SequenceFlow.class);
  }

  @Test
  public void testGetIndexName() {
    when(mockSequenceFlowIndex.getAlias()).thenReturn("sequenceFlowIndex");
    assertThat(underTest.getIndexName()).isEqualTo("sequenceFlowIndex");
    verify(mockSequenceFlowIndex, times(1)).getAlias();
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
    final SequenceFlow filter =
        new SequenceFlow()
            .setId("id")
            .setActivityId("activity_id")
            .setTenantId("tenant")
            .setProcessInstanceKey(1L);

    final Query<SequenceFlow> inputQuery = new Query<SequenceFlow>().setFilter(filter);

    underTest.buildFiltering(inputQuery, mockSearchRequest);

    // Verify that each field from the flow node filter was added as a query term to the query
    verify(mockQueryWrapper, times(1)).term(SequenceFlow.ID, filter.getId());
    verify(mockQueryWrapper, times(1)).term(SequenceFlow.ACTIVITY_ID, filter.getActivityId());
    verify(mockQueryWrapper, times(1)).term(SequenceFlow.TENANT_ID, filter.getTenantId());
    verify(mockQueryWrapper, times(1))
        .term(SequenceFlow.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey());
  }
}
