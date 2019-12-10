/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.RestStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.IndexSettingsBuilder.buildDynamicSettings;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DEFAULT_INDEX_TYPE;

@RequiredArgsConstructor
@Component
@Slf4j
public class ElasticSearchSchemaManager {
  private static final String INDEX_READ_ONLY_SETTING = "index.blocks.read_only_allow_delete";

  private final ElasticsearchMetadataService metadataService;
  private final ConfigurationService configurationService;
  private final OptimizeIndexNameService indexNameService;

  private final List<IndexMappingCreator> mappings;
  private final ObjectMapper objectMapper;

  public void validateExistingSchemaVersion(final OptimizeElasticsearchClient esClient) {
    metadataService.validateSchemaVersionCompatibility(esClient);
  }

  public void initializeSchema(final OptimizeElasticsearchClient esClient) {
    unblockIndices(esClient.getHighLevelClient());
    if (!schemaExists(esClient)) {
      log.info("Initializing Optimize schema...");
      createOptimizeIndices(esClient);
      log.info("Optimize schema initialized successfully.");
    } else {
      updateAllMappingsAndDynamicSettings(esClient.getHighLevelClient());
    }
    metadataService.initMetadataVersionIfMissing(esClient);
  }

  public void addMapping(IndexMappingCreator mapping) {
    mappings.add(mapping);
  }

  public List<IndexMappingCreator> getMappings() {
    return mappings;
  }

  public boolean schemaExists(OptimizeElasticsearchClient esClient) {
    return indicesExist(esClient, getMappings());
  }

  public boolean indexExists(final OptimizeElasticsearchClient esClient,
                             final IndexMappingCreator mapping) {
    return indicesExist(esClient, Collections.singletonList(mapping));
  }

  private boolean indicesExist(final OptimizeElasticsearchClient esClient,
                               final List<IndexMappingCreator> mappings) {
    final List<String> indices = mappings.stream().map(IndexMappingCreator::getIndexName).collect(Collectors.toList());
    final GetIndexRequest request = new GetIndexRequest();
    request.indices(indices.toArray(new String[]{}));

    try {
      return esClient.exists(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      final String message = String.format(
        "Could not check if [%s] index(es) already exist.",
        String.join(",", indices)
      );
      throw new OptimizeRuntimeException(message, e);
    }
  }

  /**
   * NOTE: create one alias and index per type
   * <p>
   * https://www.elastic.co/guide/en/elasticsearch/reference/6.0/indices-aliases.html
   */
  public void createOptimizeIndices(OptimizeElasticsearchClient esClient) {
    for (IndexMappingCreator mapping : mappings) {
      createOptimizeIndex(esClient, mapping);
    }

    RefreshRequest refreshAllIndexesRequest = new RefreshRequest();
    try {
      esClient.getHighLevelClient().indices().refresh(refreshAllIndexesRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Could not refresh Optimize indices!", e);
    }
  }

  public void createOptimizeIndex(final OptimizeElasticsearchClient esClient, final IndexMappingCreator mapping) {
    final String aliasName = indexNameService.getOptimizeIndexAliasForIndex(mapping.getIndexName());
    final String indexName = indexNameService.getVersionedOptimizeIndexNameForIndexMapping(mapping);
    final Settings indexSettings = createIndexSettings();
    try {
      try {
        CreateIndexRequest request = new CreateIndexRequest(indexName);
        request.alias(new Alias(aliasName));
        request.settings(indexSettings);
        request.mapping(DEFAULT_INDEX_TYPE, mapping.getSource());
        esClient.getHighLevelClient().indices().create(request, RequestOptions.DEFAULT);
      } catch (ElasticsearchStatusException e) {
        if (e.status() == RestStatus.BAD_REQUEST && e.getMessage().contains("resource_already_exists_exception")) {
          log.debug("index {} already exists, updating mapping and dynamic settings.", indexName);
          updateIndexDynamicSettingsAndMappings(esClient.getHighLevelClient(), mapping);
        } else {
          throw e;
        }
      }
    } catch (Exception e) {
      String message = String.format("Could not create Index [%s]", indexName);
      log.warn(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  public void deleteOptimizeIndex(final OptimizeElasticsearchClient esClient, final ProcessInstanceIndex mapping) {
    final String indexName = indexNameService.getVersionedOptimizeIndexNameForIndexMapping(mapping);
    try {
      try {
        DeleteIndexRequest request = new DeleteIndexRequest(indexName);
        esClient.getHighLevelClient().indices().delete(request, RequestOptions.DEFAULT);
      } catch (ElasticsearchStatusException e) {
        if (e.status() == RestStatus.NOT_FOUND) {
          log.debug("Index {} was not found.", indexName);
        } else {
          throw e;
        }
      }
    } catch (Exception e) {
      final String message = String.format("Could not delete Index [%s]", indexName);
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private void updateAllMappingsAndDynamicSettings(RestHighLevelClient esClient) {
    log.info("Updating Optimize schema...");
    for (IndexMappingCreator mapping : mappings) {
      updateIndexDynamicSettingsAndMappings(esClient, mapping);
    }
    log.info("Finished updating Optimize schema.");
  }

  private void unblockIndices(RestHighLevelClient esClient) {
    Map<String, Map> responseBodyAsMap;
    try {
      // we need to perform this request manually since Elasticsearch 6.5 automatically
      // adds "master_timeout" parameter to the get settings request which is not
      // recognized prior to 6.4 and throws an error. As soon as we don't support 6.3 or
      // older those lines can be replaced with the high rest client equivalent.
      Request request = new Request("GET", "/_all/_settings");
      Response response = esClient.getLowLevelClient().performRequest(request);
      String responseBody = EntityUtils.toString(response.getEntity());
      responseBodyAsMap = objectMapper.readValue(responseBody, new TypeReference<Map<String, Map>>() {
      });
    } catch (Exception e) {
      log.error("Could not retrieve index settings!", e);
      throw new OptimizeRuntimeException("Could not retrieve index settings!", e);
    }
    boolean indexBlocked = false;
    for (Map.Entry<String, Map> entry : responseBodyAsMap.entrySet()) {
      Map<String, Map> settingsMap = (Map) entry.getValue().get("settings");
      Map<String, String> indexSettingsMap = settingsMap.get("index");
      if (Boolean.parseBoolean(indexSettingsMap.get(INDEX_READ_ONLY_SETTING))
        && entry.getKey().contains(indexNameService.getIndexPrefix())) {
        indexBlocked = true;
        log.info("Found blocked Optimize Elasticsearch indices");
        break;
      }
    }

    if (indexBlocked) {
      log.info("Unblocking Elasticsearch indices...");
      UpdateSettingsRequest updateSettingsRequest = new UpdateSettingsRequest(indexNameService.getIndexPrefix() + "*");
      updateSettingsRequest.settings(Settings.builder().put(INDEX_READ_ONLY_SETTING, false));
      try {
        esClient.indices().putSettings(updateSettingsRequest, RequestOptions.DEFAULT);
      } catch (IOException e) {
        throw new OptimizeRuntimeException("Could not unblock Elasticsearch indices!", e);
      }
    }
  }

  private void updateIndexDynamicSettingsAndMappings(RestHighLevelClient esClient, IndexMappingCreator indexMapping) {
    final String indexName = indexNameService.getVersionedOptimizeIndexNameForIndexMapping(indexMapping);
    try {
      final Settings indexSettings = buildDynamicSettings(configurationService);
      final UpdateSettingsRequest updateSettingsRequest = new UpdateSettingsRequest();
      updateSettingsRequest.indices(indexName);
      updateSettingsRequest.settings(indexSettings);
      esClient.indices().putSettings(updateSettingsRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String message = String.format("Could not update index settings for index [%s].", indexMapping.getIndexName());
      throw new OptimizeRuntimeException(message, e);
    }

    try {
      final PutMappingRequest putMappingRequest = new PutMappingRequest(indexName);
      putMappingRequest.type(DEFAULT_INDEX_TYPE).source(indexMapping.getSource());
      esClient.indices().putMapping(putMappingRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String message = String.format("Could not update index mappings for index [%s].", indexMapping.getIndexName());
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private Settings createIndexSettings() {
    try {
      return IndexSettingsBuilder.buildAllSettings(configurationService);
    } catch (IOException e) {
      log.error("Could not create settings!", e);
      throw new OptimizeRuntimeException("Could not create index settings");
    }
  }

}
