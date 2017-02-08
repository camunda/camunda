package org.camunda.optimize.test.factory;

import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Transport client factory used in unit tests in order to allow mocking
 * requests towards elasticsearch
 *
 * since most of tests should not verify user initialization,
 * assume default user is always there.
 *
 * @author Askar Akhmerov
 */
public class MockTransportClientFactory implements FactoryBean<TransportClient> {
  @Autowired
  private ConfigurationService configurationService;

  @Override
  public TransportClient getObject() throws Exception {
    TransportClient mock = Mockito.mock(TransportClient.class);

    SearchRequestBuilder positiveAuthenticationSearch = setUpSearchRequestBuilder(mock);
    SearchResponse responseWithOneHit = setUpSearchResponse();
    Mockito.when(positiveAuthenticationSearch.get()).thenReturn(responseWithOneHit);

    return mock;
  }

  private SearchRequestBuilder setUpSearchRequestBuilder(TransportClient mock) {
    SearchRequestBuilder positiveAuthenticationSearch = Mockito.mock(SearchRequestBuilder.class);
    Mockito.when(mock.prepareSearch(configurationService.getOptimizeIndex())).thenReturn(positiveAuthenticationSearch);
    Mockito.when(
        positiveAuthenticationSearch.setTypes(Mockito.eq(configurationService.getElasticSearchUsersType()))
    ).thenReturn(positiveAuthenticationSearch);
    Mockito.when(positiveAuthenticationSearch.setSource(Mockito.any(SearchSourceBuilder.class)))
        .thenReturn(positiveAuthenticationSearch);
    return positiveAuthenticationSearch;
  }

  private SearchResponse setUpSearchResponse() {
    SearchResponse responseWithOneHit = Mockito.mock(SearchResponse.class);
    SearchHits searchHitsMock = Mockito.mock(SearchHits.class);
    Mockito.when(searchHitsMock.getTotalHits()).thenReturn(1L);
    Mockito.when(searchHitsMock.totalHits()).thenReturn(1L);
    Mockito.when(responseWithOneHit.getHits()).thenReturn(searchHitsMock);
    return responseWithOneHit;
  }

  @Override
  public Class<?> getObjectType() {
    return TransportClient.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
