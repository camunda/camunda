package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.sharing.SharedResourceType;
import org.camunda.optimize.dto.optimize.query.sharing.SharingDto;
import org.camunda.optimize.service.es.schema.type.ShareType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * @author Askar Akhmerov
 */
@Component
public class SharingReader {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private Client esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  private Optional<SharingDto> findShareByQuery(String resourceId, QueryBuilder query) {
    Optional<SharingDto> result = Optional.empty();
    logger.debug("Fetching share for resource [{}]", resourceId);

    SearchResponse scrollResp = esclient
      .prepareSearch(configurationService.getOptimizeIndex(configurationService.getShareType()))
      .setTypes(configurationService.getShareType())
      .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
      .setQuery(query)
      .setSize(20)
      .get();

    if (scrollResp.getHits().getTotalHits() != 0) {
      try {
        result = Optional.of(
          objectMapper.readValue(
            scrollResp.getHits().getAt(0).getSourceAsString(),
            SharingDto.class
          )
        );
      } catch (IOException e) {
        logger.error("cant't map sharing hit", e);
      }
    }
    return result;
  }

  public Optional<SharingDto> findShare(String shareId) {
    Optional<SharingDto> result = Optional.empty();
    logger.debug("Fetching share with id [{}]", shareId);
    GetResponse getResponse = esclient
      .prepareGet(
          configurationService.getOptimizeIndex(configurationService.getShareType()),
          configurationService.getShareType(),
          shareId
      )
      .setRealtime(false)
      .get();

    if (getResponse.isExists()) {
      try {
        result = Optional.of(objectMapper.readValue(getResponse.getSourceAsString(), SharingDto.class));
      } catch (IOException e) {
        logger.error("cant't map sharing hit", e);
      }
    }
    return result;
  }

  public Optional<SharingDto> findShareForResource(String resourceId, SharedResourceType type) {
    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
    boolQueryBuilder
        .must(QueryBuilders.termQuery(ShareType.RESOURCE_ID, resourceId))
        .must(QueryBuilders.termQuery(ShareType.TYPE, type.toString()));
    QueryBuilder query = boolQueryBuilder;

    return findShareByQuery(resourceId, query);
  }
}
