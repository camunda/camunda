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
package io.camunda.operate.elasticsearch.reader;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.elasticsearch.ExtendedElasticSearchClient;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import io.camunda.operate.webapp.elasticsearch.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.elasticsearch.reader.IncidentReader;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.rest.FlowNodeInstanceMetadataBuilder;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceQueryDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceRequestDto;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles({"test"})
@SpringBootTest(
    classes = {
      ObjectMapper.class,
      OperateProperties.class,
      TenantAwareElasticsearchClient.class,
      FlowNodeInstanceTemplate.class,
      FlowNodeInstanceReader.class
    })
public class FlowNodeInstanceReaderTest {

  @MockBean private DecisionInstanceTemplate decisionInstanceTemplate;

  @MockBean private IncidentTemplate incidentTemplate;

  @MockBean private ProcessCache processCache;

  @MockBean private ProcessInstanceReader processInstanceReader;

  @MockBean private IncidentReader incidentReader;

  @MockBean private FlowNodeInstanceMetadataBuilder flowNodeInstanceMetadataBuilder;

  @MockBean
  @Qualifier("esClient")
  private ExtendedElasticSearchClient mockEsClient;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private OperateProperties operateProperties;

  @Autowired private TenantAwareElasticsearchClient tenantAwareElasticsearchClient;

  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired private FlowNodeInstanceReader flowNodeInstanceReader;

  private final SearchResponse mockResponse = Mockito.mock(SearchResponse.class);

  @BeforeEach
  public void beforeEach() throws Exception {
    when(mockResponse.getHits()).thenReturn(SearchHits.empty());
    when(mockResponse.getAggregations()).thenReturn(new Aggregations(List.of()));
    when(mockEsClient.search(
            any(SearchRequest.class), any(RequestOptions.class), any(Boolean.class)))
        .thenReturn(mockResponse);
  }

  @Test
  public void shouldCheckForFailedShardsWhenPaging() throws Exception {
    final FlowNodeInstanceQueryDto query =
        new FlowNodeInstanceQueryDto()
            .setProcessInstanceId("123")
            .setTreePath("P1/F1")
            .setPageSize(10);
    final FlowNodeInstanceRequestDto request =
        new FlowNodeInstanceRequestDto().setQueries(List.of(query));

    flowNodeInstanceReader.getFlowNodeInstances(request);
    verify(mockEsClient, times(1))
        .search(any(SearchRequest.class), any(RequestOptions.class), eq(true));
  }

  @Test
  public void shouldCheckForFailedShardsWhenScrolling() throws Exception {
    final FlowNodeInstanceQueryDto query =
        new FlowNodeInstanceQueryDto()
            .setProcessInstanceId("123")
            .setTreePath("P1/F1")
            .setPageSize(null);
    final FlowNodeInstanceRequestDto request =
        new FlowNodeInstanceRequestDto().setQueries(List.of(query));

    flowNodeInstanceReader.getFlowNodeInstances(request);
    verify(mockEsClient, times(1))
        .search(any(SearchRequest.class), any(RequestOptions.class), eq(true));
  }
}
