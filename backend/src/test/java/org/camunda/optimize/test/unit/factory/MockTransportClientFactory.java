package org.camunda.optimize.test.unit.factory;

import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.mockito.AdditionalMatchers;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
public class MockTransportClientFactory implements FactoryBean<Client> {
  @Autowired
  private ConfigurationService configurationService;

  @Override
  public Client getObject() throws Exception {
    Client transportClientMock = mock(Client.class);
    SearchRequestBuilder optimizeSearchRequestBuilder = mock(SearchRequestBuilder.class);
    when(transportClientMock.prepareSearch(configurationService.getOptimizeIndex())).thenReturn(optimizeSearchRequestBuilder);

    setUpSearchRequestBuilderForAuthenticationTest(optimizeSearchRequestBuilder);
    setUpSearchRequestBuilderForImportTests(optimizeSearchRequestBuilder);
    setUpGetRequestBuilderForWriterTests(transportClientMock);

    return transportClientMock;
  }

  private void setUpGetRequestBuilderForWriterTests(Client transportClientMock) {
    GetResponse response = mock(GetResponse.class);
    GetRequestBuilder builder = mock(GetRequestBuilder.class);
    when(transportClientMock.prepareGet(anyString(), anyString(), anyString())).thenReturn(builder);
    when(builder.setRealtime(anyBoolean())).thenReturn(builder);
    when(builder.get()).thenReturn(response);
    when(response.isExists()).thenReturn(false);
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
    SearchResponse responseWithOneHit = mock(SearchResponse.class);
    SearchHits searchHitsMock = mock(SearchHits.class);
    when(searchHitsMock.getTotalHits()).thenReturn(1L);
    when(searchHitsMock.totalHits()).thenReturn(1L);
    when(responseWithOneHit.getHits()).thenReturn(searchHitsMock);
    return responseWithOneHit;
  }

  private void setUpSearchRequestBuilderForImportTests(SearchRequestBuilder builder) {
    SearchRequestBuilder newBuilder = mock(SearchRequestBuilder.class);
    when(
      builder.setTypes(AdditionalMatchers.not(eq(configurationService.getElasticSearchUsersType())))
    ).thenReturn(newBuilder);
    when(newBuilder.setQuery(any())).thenReturn(newBuilder);
    when(newBuilder.setFetchSource(false)).thenReturn(newBuilder);
    when(newBuilder.setSize(anyInt())).thenReturn(newBuilder);
    SearchResponse response = createImportTestSearchResponse();
    when(newBuilder.get()).thenReturn(response);
  }

  private SearchResponse createImportTestSearchResponse() {
    SearchResponse responseWithOneHit = mock(SearchResponse.class);
    SearchHits searchHitsMock = mock(SearchHits.class);
    SearchHit[] searchHits = {};
    when(searchHitsMock.getTotalHits()).thenReturn(0L);
    when(responseWithOneHit.getHits()).thenReturn(searchHitsMock);
    when(searchHitsMock.getHits()).thenReturn(searchHits);
    return responseWithOneHit;
  }

  @Override
  public Class<?> getObjectType() {
    return Client.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
