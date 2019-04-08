/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.metadata.Version;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
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
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

@Component
public class ElasticsearchMetadataService {
  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchMetadataService.class);

  private static final String ID = "1";
  private static final String ERROR_MESSAGE_ES_REQUEST = "Could not write Optimize version to Elasticsearch.";

  private static final String CURRENT_OPTIMIZE_VERSION = Version.VERSION;

  private final ObjectMapper objectMapper;

  @Autowired
  public ElasticsearchMetadataService(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public void initMetadataVersionIfMissing(final RestHighLevelClient esClient) {
    readMetadata(esClient).orElseGet(() -> {
      writeMetadata(esClient, new MetadataDto(CURRENT_OPTIMIZE_VERSION));
      return null;
    });
  }

  public void validateSchemaVersionCompatibility(final RestHighLevelClient esClient) {
    readMetadata(esClient).ifPresent((metadataDto) -> {
      if (!CURRENT_OPTIMIZE_VERSION.equals(metadataDto.getSchemaVersion())) {
        final String errorMessage = String.format(
          "The Elasticsearch Optimize schema version [%s] doesn't match the current Optimize version [%s]."
            + " Please make sure to run the Upgrade first.",
          metadataDto.getSchemaVersion(),
          CURRENT_OPTIMIZE_VERSION
        );
        throw new OptimizeRuntimeException(errorMessage);
      }
    });
  }

  public Optional<MetadataDto> readMetadata(final RestHighLevelClient esClient) {
    Optional<MetadataDto> result = Optional.empty();

    final SearchRequest searchRequest = new SearchRequest(getOptimizeIndexAliasForType(METADATA_TYPE));
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(QueryBuilders.matchAllQuery());
    searchRequest.types(METADATA_TYPE);
    searchRequest.source(searchSourceBuilder);

    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);

      long totalHits = searchResponse.getHits().getTotalHits();
      if (totalHits == 1) {
        try {
          MetadataDto parsed = objectMapper.readValue(
            searchResponse.getHits().getAt(0).getSourceAsString(),
            MetadataDto.class
          );
          result = Optional.ofNullable(parsed);
        } catch (IOException e) {
          logger.error("can't parse metadata", e);
        }
      } else if (totalHits > 1) {
        throw new OptimizeRuntimeException("Metadata search returned [" + totalHits + "] hits");
      }
    } catch (IOException | ElasticsearchException e) {
      logger.info(
        "Was not able to retrieve metaData index, schema might not have been initialized yet if this is the first " +
          "startup!"
      );
    }

    return result;
  }

  public void writeMetadata(final RestHighLevelClient esClient, final MetadataDto metadataDto) {
    try {
      final String source = objectMapper.writeValueAsString(metadataDto);

      final IndexRequest request = new IndexRequest(getOptimizeIndexAliasForType(METADATA_TYPE), METADATA_TYPE, ID)
        .source(source, XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);

      final IndexResponse indexResponse = esClient.index(request, RequestOptions.DEFAULT);

      if (!indexResponse.getResult().equals(IndexResponse.Result.CREATED)
        && !indexResponse.getResult().equals(IndexResponse.Result.UPDATED)) {
        logger.error(ERROR_MESSAGE_ES_REQUEST);
        throw new OptimizeRuntimeException(ERROR_MESSAGE_ES_REQUEST);
      }
    } catch (IOException e) {
      logger.error(ERROR_MESSAGE_ES_REQUEST, e);
      throw new OptimizeRuntimeException(ERROR_MESSAGE_ES_REQUEST, e);
    }
  }
}
