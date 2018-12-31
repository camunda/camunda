package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.METADATA_TYPE;


@Component
public class MetadataReader {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private TransportClient esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  public Optional<MetadataDto> readMetadata() {
    Optional<MetadataDto> result = Optional.empty();

    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(METADATA_TYPE));
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(QueryBuilders.matchAllQuery());
    searchRequest.types(METADATA_TYPE);
    searchRequest.source(searchSourceBuilder);

    SearchResponse searchResponse = esclient.search(searchRequest).actionGet();

    long totalHits = searchResponse.getHits().getTotalHits();
    if (totalHits == 1) {
      try {
        MetadataDto parsed = objectMapper.readValue(searchResponse.getHits().getAt(0).getSourceAsString(), MetadataDto.class);
        result = Optional.ofNullable(parsed);
      } catch (IOException e) {
        logger.error("can't parse metadata", e);
      }
    } else if (totalHits > 1) {
      throw new OptimizeRuntimeException("Metadata search returned [" + totalHits + "] hits");
    }

    return result;
  }
}
