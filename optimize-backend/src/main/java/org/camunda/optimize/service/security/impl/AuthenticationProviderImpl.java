package org.camunda.optimize.service.security.impl;

import org.camunda.optimize.service.exceptions.UnauthorizedUserException;
import org.camunda.optimize.service.security.AuthenticationProvider;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.jvnet.hk2.annotations.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @author Askar Akhmerov
 */
@Component
public class AuthenticationProviderImpl implements AuthenticationProvider {
  private static final String ADMIN = "admin";

  @Autowired
  private TransportClient client;

  public void authenticate(String username, String password) throws UnauthorizedUserException {
    SearchResponse response = client.prepareSearch("optimize")
        .setTypes("users")
        .setQuery(QueryBuilders.boolQuery()
            .must(QueryBuilders.termQuery("username" , username))
            .must(QueryBuilders.termQuery("password" , password))
        )
        .get();
    if (response.getHits().totalHits() <= 0) throw new UnauthorizedUserException();
  }

  public TransportClient getClient() {
    return client;
  }

  public void setClient(TransportClient client) {
    this.client = client;
  }
}
