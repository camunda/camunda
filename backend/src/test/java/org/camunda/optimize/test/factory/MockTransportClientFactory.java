package org.camunda.optimize.test.factory;

import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.mockito.AdditionalMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

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
    TransportClient transportClientMock = Mockito.mock(TransportClient.class);
    SearchRequestBuilder optimizeSearchRequestBuilder = Mockito.mock(SearchRequestBuilder.class);
    when(transportClientMock.prepareSearch(configurationService.getOptimizeIndex())).thenReturn(optimizeSearchRequestBuilder);

    setUpSearchRequestBuilderForAuthenticationTest(optimizeSearchRequestBuilder);
    setUpSearchRequestBuilderForImportTests(optimizeSearchRequestBuilder);

    return transportClientMock;
  }

  private void setUpSearchRequestBuilderForAuthenticationTest(SearchRequestBuilder positiveAuthenticationSearch) {
    when(
        positiveAuthenticationSearch.setTypes(eq(configurationService.getElasticSearchUsersType()))
    ).thenReturn(positiveAuthenticationSearch);
    when(positiveAuthenticationSearch.setSource(any(SearchSourceBuilder.class)))
        .thenReturn(positiveAuthenticationSearch);
    SearchResponse responseWithOneHit = setUpSearchResponse();
    when(positiveAuthenticationSearch.get()).thenReturn(responseWithOneHit);
  }

  private SearchResponse setUpSearchResponse() {
    SearchResponse responseWithOneHit = Mockito.mock(SearchResponse.class);
    SearchHits searchHitsMock = Mockito.mock(SearchHits.class);
    when(searchHitsMock.getTotalHits()).thenReturn(1L);
    when(searchHitsMock.totalHits()).thenReturn(1L);
    when(responseWithOneHit.getHits()).thenReturn(searchHitsMock);
    return responseWithOneHit;
  }

  private void setUpSearchRequestBuilderForImportTests(SearchRequestBuilder builder) {
    SearchRequestBuilder newBuilder = Mockito.mock(SearchRequestBuilder.class);
    when(
      builder.setTypes(AdditionalMatchers.not(eq(configurationService.getElasticSearchUsersType())))
    ).thenReturn(newBuilder);
    when(newBuilder.setQuery(any())).thenReturn(newBuilder);
    when(newBuilder.setSize(anyInt())).thenReturn(newBuilder);
    SearchResponse response = createImportTestSearchResponse();
    when(newBuilder.get()).thenReturn(response);
  }

  private SearchResponse createImportTestSearchResponse() {
    SearchResponse responseWithOneHit = Mockito.mock(SearchResponse.class);
    SearchHits searchHitsMock = Mockito.mock(SearchHits.class);
    SearchHit[] searchHits = {};
    when(searchHitsMock.getTotalHits()).thenReturn(0L);
    when(responseWithOneHit.getHits()).thenReturn(searchHitsMock);
    when(searchHitsMock.getHits()).thenReturn(searchHits);
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
