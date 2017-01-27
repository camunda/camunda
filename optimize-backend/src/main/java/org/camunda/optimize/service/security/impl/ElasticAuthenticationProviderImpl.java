package org.camunda.optimize.service.security.impl;

import org.camunda.optimize.service.security.AuthenticationProvider;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Askar Akhmerov
 */
@Component ("elasticAuthenticationProvider")
public class ElasticAuthenticationProviderImpl implements AuthenticationProvider {

  @Autowired
  private TransportClient client;

  @Autowired
  private ConfigurationService configurationService;

  public boolean authenticate(String username, String password) {
    boolean authenticated = true;
    SearchResponse response = client.prepareSearch(configurationService.getOptimizeIndex())
        .setTypes("users")
        .setQuery(QueryBuilders.boolQuery()
            .must(QueryBuilders.termQuery("username" , username))
            .must(QueryBuilders.termQuery("password" , password))
        )
        .get();
    if (response.getHits().totalHits() <= 0) {
      authenticated = false;
    }

    return authenticated;
  }

  public TransportClient getClient() {
    return client;
  }

  public void setClient(TransportClient client) {
    this.client = client;
  }
}
