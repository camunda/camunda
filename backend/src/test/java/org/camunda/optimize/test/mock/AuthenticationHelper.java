package org.camunda.optimize.test.mock;

import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHits;
import org.mockito.Mockito;

/**
 * Class that provide useful mocking methods for various unit and integration tests.
 *
 * @author Askar Akhmerov
 */
public class AuthenticationHelper {

  public static void setupPositiveUserQuery(TransportClient esClientMock, ConfigurationService configurationService) {
    SearchRequestBuilder mockSearch = Mockito.mock(SearchRequestBuilder.class);
    Mockito.when(mockSearch.setTypes(Mockito.anyString())).thenReturn(mockSearch);
    Mockito.when(mockSearch.setQuery(Mockito.any(QueryBuilder.class))).thenReturn(mockSearch);
    SearchResponse mockSearchResponse = Mockito.mock(SearchResponse.class);
    SearchHits mockHist = Mockito.mock(SearchHits.class);
    Mockito.when(mockHist.totalHits()).thenReturn(1L);
    Mockito.when(mockSearchResponse.getHits()).thenReturn(mockHist);
    Mockito.when(mockSearch.get()).thenReturn(mockSearchResponse);
    Mockito.when(esClientMock.prepareSearch(configurationService.getOptimizeIndex())).thenReturn(mockSearch);
  }
}
