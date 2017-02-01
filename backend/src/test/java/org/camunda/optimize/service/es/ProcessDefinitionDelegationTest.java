package org.camunda.optimize.service.es;

import org.camunda.optimize.dto.engine.ProcessDefinitionDto;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.test.AbstractJerseyTest;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/applicationContext.xml" })
public class ProcessDefinitionDelegationTest extends AbstractJerseyTest{

  @Autowired
  private TransportClient mockedTransportClient;

  @Autowired
  private ConfigurationService configurationService;

  @Test
  public void test() {

    // given some mocks
    SearchRequestBuilder searchRequestBuilder = mock(SearchRequestBuilder.class);
    mockReaderCalls(searchRequestBuilder);

    // when
    Response response =
      target("process-definition")
      .request()
      .get();

    // then the staus code is okay and the mocks were invoked
    assertThat(response.getStatus(), is(200));
    List<ProcessDefinitionDto> definitions =
      response.readEntity(new GenericType<List<ProcessDefinitionDto>>(){});
    assertThat(definitions,is(notNullValue()));

    verify(mockedTransportClient, times(1)).prepareSearch(configurationService.getOptimizeIndex());
    verify(searchRequestBuilder, times(1)).setTypes(configurationService.getProcessDefinitionType());
  }

  private void mockReaderCalls(SearchRequestBuilder searchRequestBuilder) {
    SearchResponse searchResponse = mock(SearchResponse.class);
    SearchHits searchHits = mock(SearchHits.class);
    SearchHit[] hits = {};

    when(mockedTransportClient.prepareSearch(anyString())).thenReturn(searchRequestBuilder);
    when(searchRequestBuilder.setTypes(anyString())).thenReturn(searchRequestBuilder);
    when(searchRequestBuilder.setQuery(any())).thenReturn(searchRequestBuilder);
    when(searchRequestBuilder.get()).thenReturn(searchResponse);
    when(searchResponse.getHits()).thenReturn(searchHits);
    when(searchHits.totalHits()).thenReturn(0L);
    when(searchHits.getHits()).thenReturn(hits);
  }


}
