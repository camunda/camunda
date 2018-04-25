package org.camunda.optimize.service.security.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.user.CredentialsDto;
import org.camunda.optimize.service.es.schema.type.UserType;
import org.camunda.optimize.service.es.writer.UserWriter;
import org.camunda.optimize.service.security.AuthenticationProvider;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.service.es.schema.type.UserType.PASSWORD;
import static org.camunda.optimize.service.es.schema.type.UserType.USER_ID;
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

  @Autowired
  private UserWriter userWriter;

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
    try {
      userWriter.deleteUser(configurationService.getDefaultUser());
    } catch (Exception e) {
      logger.warn("Could not delete default user!");
    }
  }

  private void addDefaultUser() {
    CredentialsDto user = new CredentialsDto();
    user.setId(configurationService.getDefaultUser());
    user.setPassword(configurationService.getDefaultPassword());

    try {
      userWriter.createNewUser(user, user.getId());
    } catch (Exception e) {
      logger.error("Can't write default user to elasticsearch", e);
    }
  }

  public boolean authenticate(CredentialsDto credentialsDto) {
    this.initialize();
    boolean authenticated = true;
    SearchResponse response = client.prepareSearch(
        configurationService.getOptimizeIndex(configurationService.getElasticSearchUsersType())
    )
    .setTypes(configurationService.getElasticSearchUsersType())
    .setQuery(QueryBuilders.boolQuery()
        .must(termQuery(USER_ID, credentialsDto.getId()))
        .must(termQuery(PASSWORD, credentialsDto.getPassword()))
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
