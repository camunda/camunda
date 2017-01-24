package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.CredentialsTO;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.test.AbstractJerseyTest;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHits;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;

/**
 * @author Askar Akhmerov
 */
public class AuthenticationTest extends AbstractJerseyTest {
  @Autowired
  private TransportClient esClientMock;
  @Autowired
  private ConfigurationService configurationService;

  @Test
  public void authenticateUser() throws Exception {
    //when
    Response response = authenticateAdmin();

    //then
    assertThat(response.getStatus(),is(200));
    String responseEntity = response.readEntity(String.class);
    assertThat(responseEntity,is(notNullValue()));
  }

  private Response authenticateAdmin() {
    CredentialsTO entity = new CredentialsTO();
    entity.setUsername("admin");
    entity.setPassword("admin");

    setupPositiveUserQuery();

    return target("authentication")
        .request()
        .post(Entity.json(entity));
  }

  private void setupPositiveUserQuery() {
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

  @Test
  public void logout() throws Exception {
    //given
    Response response = authenticateAdmin();
    String token = response.readEntity(String.class);

    //when
    Response logoutResponse = target("authentication/logout")
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
        .get();

    //then
    assertThat(logoutResponse.getStatus(),is(200));
    String responseEntity = logoutResponse.readEntity(String.class);
    assertThat(responseEntity,is("OK"));
  }

  @Test
  public void logoutSecure() throws Exception {

    //when
    Response logoutResponse = target("authentication/logout")
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + "randomToken")
        .get();

    //then
    assertThat(logoutResponse.getStatus(),is(401));
  }

}