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
package io.camunda.operate.elasticsearch.dao;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.MetricEntity;
import io.camunda.operate.schema.indices.MetricIndex;
import io.camunda.operate.store.elasticsearch.dao.GenericDAO;
import java.io.IOException;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.xcontent.XContentType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GenericDAOTest {

  @Mock private RestHighLevelClient esClient;
  @Mock private ObjectMapper objectMapper;
  @Mock private MetricIndex index;
  @Mock private MetricEntity entity;

  @Test
  public void instantiateWithoutObjectMapperThrowsException() {
    Assertions.assertThrows(
        IllegalStateException.class,
        () ->
            new GenericDAO.Builder<MetricEntity, MetricIndex>()
                .esClient(esClient)
                .index(index)
                .build());
  }

  @Test
  public void instantiateWithoutESClientThrowsException() {
    Assertions.assertThrows(
        IllegalStateException.class,
        () ->
            new GenericDAO.Builder<MetricEntity, MetricIndex>()
                .objectMapper(objectMapper)
                .index(index)
                .build());
  }

  @Test
  public void instantiateWithoutIndexThrowsException() {
    Assertions.assertThrows(
        IllegalStateException.class,
        () ->
            new GenericDAO.Builder<MetricEntity, MetricIndex>()
                .objectMapper(objectMapper)
                .esClient(esClient)
                .build());
  }

  @Test
  @Disabled("Skipping this test as we can't mock esClient final methods")
  public void insertShouldReturnExpectedResponse() throws IOException {
    // Given
    final GenericDAO<MetricEntity, MetricIndex> dao = instantiateDao();
    final String indexName = "indexName";
    final String json = "json";
    when(index.getIndexName()).thenReturn(indexName);
    when(entity.getId()).thenReturn(null);
    when(objectMapper.writeValueAsString(any())).thenReturn(json);

    final IndexRequest request =
        new IndexRequest(indexName).id(null).source(json, XContentType.JSON);

    // When
    dao.insert(entity);

    // Then
    verify(esClient).index(request, RequestOptions.DEFAULT);
  }

  private GenericDAO<MetricEntity, MetricIndex> instantiateDao() {
    return new GenericDAO.Builder<MetricEntity, MetricIndex>()
        .esClient(esClient)
        .index(index)
        .objectMapper(objectMapper)
        .build();
  }
}
