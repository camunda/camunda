package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.user.OptimizeUserDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


@Component
public class UserReader {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private Client esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  public OptimizeUserDto getUser(String userId) {
    logger.debug("Fetching user with id [{}]", userId);
    GetResponse getResponse = esclient
      .prepareGet(
        configurationService.getOptimizeIndex(configurationService.getElasticSearchUsersType()),
        configurationService.getElasticSearchUsersType(),
        userId
      )
      .setRealtime(false)
      .get();

    if (getResponse.isExists()) {
      String responseAsString = getResponse.getSourceAsString();
      OptimizeUserDto userDto;
      try {
        userDto = objectMapper.readValue(responseAsString, OptimizeUserDto.class);
      } catch (IOException e) {
        String reason = "Error occurred while retrieving user with id [" + userId +
          "]. Could not deserialize user from Elasticsearch!";
        logger.error(reason, e);
        throw new OptimizeRuntimeException(reason);
      }
      return userDto;
    } else {
      String reason = "Was not able to retrieve user with id [" + userId +
        "] from Elasticsearch. User does not exist.";
      logger.error(reason);
      throw new OptimizeRuntimeException(reason);
    }
  }

  public List<OptimizeUserDto> getAllUsers() throws IOException {
    logger.debug("Fetching all available users");
    SearchResponse scrollResp = esclient
      .prepareSearch(configurationService.getOptimizeIndex(configurationService.getElasticSearchUsersType()))
      .setTypes(configurationService.getElasticSearchUsersType())
      .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
      .setQuery(QueryBuilders.matchAllQuery())
      .setSize(100)
      .get();
    List<OptimizeUserDto> users = new ArrayList<>();

    do {
      for (SearchHit hit : scrollResp.getHits().getHits()) {
        String responseAsString = hit.getSourceAsString();
        OptimizeUserDto persistenceDto =
          objectMapper.readValue(responseAsString, OptimizeUserDto.class);
        users.add(persistenceDto);
      }

      scrollResp = esclient
        .prepareSearchScroll(scrollResp.getScrollId())
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .get();
    } while (scrollResp.getHits().getHits().length != 0);

    return users;
  }


}
