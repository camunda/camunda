/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
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
public class OpensearchKeyFilteringDaoTest {

  @Mock private OpensearchQueryDSLWrapper mockQueryWrapper;

  @Mock private OpensearchRequestDSLWrapper mockRequestWrapper;

  @Mock private RichOpenSearchClient mockOpensearchClient;

  private OpensearchKeyFilteringDao<Object, Object> underTest;

  @BeforeEach
  public void setup() {
    underTest =
        new OpensearchKeyFilteringDao<>(
            mockQueryWrapper, mockRequestWrapper, mockOpensearchClient) {
          @Override
          protected String getKeyFieldName() {
            return "key";
          }

          @Override
          protected String getByKeyServerReadErrorMessage(final Long key) {
            return "server read error";
          }

          @Override
          protected String getByKeyNoResultsErrorMessage(final Long key) {
            return "no results error";
          }

          @Override
          protected String getByKeyTooManyResultsErrorMessage(final Long key) {
            return "too many results error";
          }

          @Override
          protected String getUniqueSortKey() {
            return null;
          }

          @Override
          protected Class<Object> getInternalDocumentModelClass() {
            return Object.class;
          }

          @Override
          protected String getIndexName() {
            return "index";
          }

          @Override
          protected void buildFiltering(
              final Query<Object> query, final SearchRequest.Builder request) {}

          @Override
          protected Object convertInternalToApiResult(final Object internalResult) {
            return internalResult;
          }
        };
  }

  @Test
  public void testByKeyWithServerException() {
    // Mock the request building classes
    final SearchRequest.Builder mockRequestBuilder = Mockito.mock(SearchRequest.Builder.class);
    final org.opensearch.client.opensearch._types.query_dsl.Query mockOsQuery =
        Mockito.mock(org.opensearch.client.opensearch._types.query_dsl.Query.class);

    when(mockRequestWrapper.searchRequestBuilder(underTest.getIndexName()))
        .thenReturn(mockRequestBuilder);
    when(mockQueryWrapper.withTenantCheck(any())).thenReturn(mockOsQuery);
    when(mockRequestBuilder.query(mockOsQuery)).thenReturn(mockRequestBuilder);

    // Set the mocked opensearch client to throw an exception
    when(mockOpensearchClient.doc()).thenThrow(new RuntimeException());

    final Exception exception = assertThrows(ServerException.class, () -> underTest.byKey(1L));

    assertThat(exception.getMessage()).isEqualTo(underTest.getByKeyServerReadErrorMessage(1L));
  }

  @Test
  public void testByKeyWithNullKey() {
    assertThrows(ServerException.class, () -> underTest.byKey(null));
  }

  @Test
  public void testByKeyWithEmptyResults() {
    // Mock the request building classes
    final SearchRequest.Builder mockRequestBuilder = Mockito.mock(SearchRequest.Builder.class);
    final org.opensearch.client.opensearch._types.query_dsl.Query mockOsQuery =
        Mockito.mock(org.opensearch.client.opensearch._types.query_dsl.Query.class);
    final OpenSearchDocumentOperations mockDocumentOperations =
        Mockito.mock(OpenSearchDocumentOperations.class);

    when(mockRequestWrapper.searchRequestBuilder(underTest.getIndexName()))
        .thenReturn(mockRequestBuilder);
    when(mockQueryWrapper.withTenantCheck(any())).thenReturn(mockOsQuery);
    when(mockRequestBuilder.query(mockOsQuery)).thenReturn(mockRequestBuilder);

    // Set the mocked opensearch client to return the mocked response
    when(mockOpensearchClient.doc()).thenReturn(mockDocumentOperations);
    when(mockDocumentOperations.searchValues(any(), any())).thenReturn(Collections.emptyList());

    final Exception exception =
        assertThrows(ResourceNotFoundException.class, () -> underTest.byKey(1L));

    assertThat(exception.getMessage()).isEqualTo(underTest.getByKeyNoResultsErrorMessage(1L));
  }

  @Test
  public void testByKeyWithValidResult() {
    // Mock the request building classes
    final SearchRequest.Builder mockRequestBuilder = Mockito.mock(SearchRequest.Builder.class);
    final org.opensearch.client.opensearch._types.query_dsl.Query mockOsQuery =
        Mockito.mock(org.opensearch.client.opensearch._types.query_dsl.Query.class);
    final OpenSearchDocumentOperations mockDocumentOperations =
        Mockito.mock(OpenSearchDocumentOperations.class);

    when(mockRequestWrapper.searchRequestBuilder(underTest.getIndexName()))
        .thenReturn(mockRequestBuilder);
    when(mockQueryWrapper.withTenantCheck(any())).thenReturn(mockOsQuery);
    when(mockRequestBuilder.query(mockOsQuery)).thenReturn(mockRequestBuilder);

    final Object validResult = new Object();

    // Set the mocked opensearch client to return the mocked response
    when(mockOpensearchClient.doc()).thenReturn(mockDocumentOperations);
    when(mockDocumentOperations.searchValues(any(), any())).thenReturn(List.of(validResult));

    // Verify the result returned is the same as the one that came directly from opensearch
    final Object result = underTest.byKey(1L);
    assertThat(result).isSameAs(validResult);
  }

  @Test
  public void testByKeyWithMultipleResult() {
    // Mock the request building classes
    final SearchRequest.Builder mockRequestBuilder = Mockito.mock(SearchRequest.Builder.class);
    final org.opensearch.client.opensearch._types.query_dsl.Query mockOsQuery =
        Mockito.mock(org.opensearch.client.opensearch._types.query_dsl.Query.class);
    final OpenSearchDocumentOperations mockDocumentOperations =
        Mockito.mock(OpenSearchDocumentOperations.class);

    when(mockRequestWrapper.searchRequestBuilder(underTest.getIndexName()))
        .thenReturn(mockRequestBuilder);
    when(mockQueryWrapper.withTenantCheck(any())).thenReturn(mockOsQuery);
    when(mockRequestBuilder.query(mockOsQuery)).thenReturn(mockRequestBuilder);

    // Set the mocked opensearch client to return the mocked response
    when(mockOpensearchClient.doc()).thenReturn(mockDocumentOperations);
    when(mockDocumentOperations.searchValues(any(), any()))
        .thenReturn(List.of(new Object(), new Object()));

    final Exception exception = assertThrows(ServerException.class, () -> underTest.byKey(1L));

    assertThat(exception.getMessage()).isEqualTo(underTest.getByKeyTooManyResultsErrorMessage(1L));
  }

  @Test
  public void testSearchByKey() {
    final SearchRequest.Builder mockRequestBuilder = Mockito.mock(SearchRequest.Builder.class);
    final org.opensearch.client.opensearch._types.query_dsl.Query mockOsQuery =
        Mockito.mock(org.opensearch.client.opensearch._types.query_dsl.Query.class);

    when(mockRequestWrapper.searchRequestBuilder("index")).thenReturn(mockRequestBuilder);
    when(mockQueryWrapper.withTenantCheck(any())).thenReturn(mockOsQuery);
    when(mockRequestBuilder.query(mockOsQuery)).thenReturn(mockRequestBuilder);

    final OpenSearchDocumentOperations mockDoc = Mockito.mock(OpenSearchDocumentOperations.class);
    when(mockOpensearchClient.doc()).thenReturn(mockDoc);

    final List<Object> validResults = Collections.singletonList(new Object());
    when(mockDoc.searchValues(any(), any())).thenReturn(validResults);

    final List<Object> results = underTest.searchByKey(1L);

    // Verify the request was built with a tenant check, the index name, and permissive matching
    assertThat(results).containsExactlyElementsOf(validResults);
    verify(mockQueryWrapper, times(1)).term("key", 1L);
    verify(mockQueryWrapper, times(1)).withTenantCheck(any());
    verify(mockRequestWrapper, times(1)).searchRequestBuilder("index");
  }

  @Test
  public void testValidateKeyWithNull() {
    assertThrows(ServerException.class, () -> underTest.validateKey(null));
  }

  @Test
  public void testValidateKey() {
    try {
      underTest.validateKey(1L);
    } catch (final ServerException e) {
      fail("Unexpected server exception on non-null key");
    }
  }
}
