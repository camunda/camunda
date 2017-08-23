package org.camunda.optimize.service.security.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.CredentialsDto;
import org.camunda.optimize.service.security.AuthenticationProvider;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

/**
 * @author Askar Akhmerov
 */
@Component ("elasticAuthenticationProvider")
public class ElasticAuthenticationProviderImpl implements AuthenticationProvider<CredentialsDto>, ConfigurationReloadable {
  private static final Logger logger = LoggerFactory.getLogger(ElasticAuthenticationProviderImpl.class);
  private boolean initialized = false;

  @Autowired
  private Client client;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ObjectMapper objectMapper;

  private void initialize() {
    if (!initialized) {
      addDefaultUserIfCreationIsEnabled();
      initialized = true;
    }
  }

  private void addDefaultUserIfCreationIsEnabled() {
    if (configurationService.isDefaultUserCreationEnabled()) {
      addDefaultUser();
    } else {
      deleteDefaultUserIfExist();
    }
  }

  private void deleteDefaultUserIfExist() {
    client.prepareDelete(
      configurationService.getOptimizeIndex(),
      configurationService.getElasticSearchUsersType(),
      configurationService.getDefaultUser()
    )
      .setRefreshPolicy(IMMEDIATE)
      .get();
  }

  private void addDefaultUser() {
    CredentialsDto user = new CredentialsDto();
    user.setUsername(configurationService.getDefaultUser());
    user.setPassword(configurationService.getDefaultPassword());

    try {
      client
        .prepareIndex(
          configurationService.getOptimizeIndex(),
          configurationService.getElasticSearchUsersType(),
          configurationService.getDefaultUser()
        )
        .setRefreshPolicy(IMMEDIATE)
        .setSource(objectMapper.writeValueAsString(user), XContentType.JSON)
        .get();
    } catch (Exception e) {
      logger.error("Can't write default user to elasticsearch", e);
    }
  }

  public boolean authenticate(CredentialsDto credentialsDto) {
    this.initialize();
    boolean authenticated = true;
    SearchResponse response = client.prepareSearch(configurationService.getOptimizeIndex())
        .setTypes(configurationService.getElasticSearchUsersType())
        .setQuery(QueryBuilders.boolQuery()
            .must(termQuery("username" , credentialsDto.getUsername()))
            .must(termQuery("password" , credentialsDto.getPassword()))
        )
        .get();
    if (response.getHits().getTotalHits() <= 0) {
      authenticated = false;
    }

    return authenticated;
  }

  @Override
  public void reloadConfiguration(ApplicationContext context) {
    this.initialized = false;
    initialize();
  }

  public Client getClient() {
    return client;
  }

  public void setClient(Client client) {
    this.client = client;
  }
}
