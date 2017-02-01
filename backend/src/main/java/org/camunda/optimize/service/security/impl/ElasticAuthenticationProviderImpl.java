package org.camunda.optimize.service.security.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.CredentialsDto;
import org.camunda.optimize.service.security.AuthenticationProvider;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;

/**
 * @author Askar Akhmerov
 */
@Component ("elasticAuthenticationProvider")
public class ElasticAuthenticationProviderImpl implements AuthenticationProvider {
  private static final Logger logger = LoggerFactory.getLogger(ElasticAuthenticationProviderImpl.class);

  @Autowired
  private TransportClient client;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ObjectMapper objectMapper;

  @PostConstruct
  private void init() {
    try {
      SearchResponse searchResponse = client
          .prepareSearch(configurationService.getOptimizeIndex())
          .setTypes(configurationService.getElasticSearchUsersType())
          .setSource(new SearchSourceBuilder().size(0))
          .get();
      if (searchResponse.getHits().totalHits() <= 0) {
        addDefaultUser();
      }

    } catch (IndexNotFoundException e) {
      //should never happen, but just in case
      addDefaultUser();
    }
  }

  private void addDefaultUser() {
    BulkRequestBuilder bulkRequest = client.prepareBulk();
    CredentialsDto user = new CredentialsDto();
    user.setUsername("admin");
    user.setPassword("admin");


    try {
      bulkRequest.add(client
          .prepareIndex(
              configurationService.getOptimizeIndex(),
              configurationService.getElasticSearchUsersType(),
              "1"
          )
          .setSource(objectMapper.writeValueAsString(user)));
      bulkRequest.execute().get();
    } catch (Exception e) {
      logger.error("Can't write default user to elasticsearch", e);
    }
  }

  public boolean authenticate(CredentialsDto credentialsDto) {
    boolean authenticated = true;
    SearchResponse response = client.prepareSearch(configurationService.getOptimizeIndex())
        .setTypes(configurationService.getElasticSearchUsersType())
        .setQuery(QueryBuilders.boolQuery()
            .must(termQuery("username" , credentialsDto.getUsername()))
            .must(termQuery("password" , credentialsDto.getPassword()))
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
