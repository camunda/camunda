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

import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Variable;
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
