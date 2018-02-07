package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.sharing.SharingDto;
import org.camunda.optimize.service.es.schema.type.ShareType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
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

  public Optional<SharingDto> findSharedResource(SharingDto createSharingDto) {
    String resourceId = createSharingDto.getResourceId();
    return findShareForResource(resourceId);
  }

  public Optional<SharingDto> findShareForResource(String resourceId) {
    Optional<SharingDto> result = Optional.empty();
    logger.debug("Fetching share for resource [{}]", resourceId);
    QueryBuilder query;
    query = QueryBuilders.termQuery(ShareType.RESOURCE_ID, resourceId);

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
}
