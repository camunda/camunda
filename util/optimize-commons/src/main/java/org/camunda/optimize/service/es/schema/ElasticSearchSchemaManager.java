/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.cluster.settings.ClusterUpdateSettingsRequest;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.rest.RestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.IndexSettingsBuilder.buildDynamicSettings;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.OPTIMIZE_INDEX_PREFIX;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getVersionedOptimizeIndexNameForTypeMapping;

@Component
public class ElasticSearchSchemaManager {

  private static final Logger logger = LoggerFactory.getLogger(ElasticSearchSchemaManager.class);
  private static final String INDEX_READ_ONLY_SETTING = "index.blocks.read_only_allow_delete";
  private static final ToXContent.Params XCONTENT_PARAMS_FLAT_SETTINGS =
    new ToXContent.MapParams(Collections.singletonMap("flat_settings", "true"));

  private final ElasticsearchMetadataService metadataService;

  private ConfigurationService configurationService;
  private List<TypeMappingCreator> mappings;
  private ObjectMapper objectMapper;

  @Autowired
  public ElasticSearchSchemaManager(final ElasticsearchMetadataService metadataService,
                                    final ConfigurationService configurationService,
                                    final List<TypeMappingCreator> mappings,
                                    final ObjectMapper objectMapper) {
    this.metadataService = metadataService;
    this.configurationService = configurationService;
    this.mappings = mappings;
    this.objectMapper = objectMapper;
  }

  public void validateExistingSchemaVersion(final RestHighLevelClient esClient) {
    metadataService.validateSchemaVersionCompatibility(esClient);
  }

  public void initializeSchema(final RestHighLevelClient esClient) {
    unblockIndices(esClient);
    if (!schemaAlreadyExists(esClient)) {
      logger.info("Initializing Optimize schema...");
      createOptimizeIndices(esClient);
      logger.info("Optimize schema initialized successfully.");
    } else {
      updateAllMappingsAndDynamicSettings(esClient);
    }
    metadataService.initMetadataVersionIfMissing(esClient);
  }

  public void addMapping(TypeMappingCreator mapping) {
    mappings.add(mapping);
  }

  public List<TypeMappingCreator> getMappings() {
    return mappings;
  }

  public boolean schemaAlreadyExists(RestHighLevelClient esClient) {
    String[] optimizeIndex = new String[mappings.size()];
    int i = 0;
    for (TypeMappingCreator creator : mappings) {
      optimizeIndex[i] = getOptimizeIndexAliasForType(creator.getType());
      i = ++i;
    }

    GetIndexRequest request = new GetIndexRequest();
    request.indices(optimizeIndex);

    try {
      return esClient.indices().exists(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String message = String.format(
        "Could not check if [%s] index(es) already exist.",
        String.join(",", optimizeIndex)
      );
      throw new OptimizeRuntimeException(message, e);
    }
  }


  /**
   * NOTE: create one alias and index per type
   * <p>
   * https://www.elastic.co/guide/en/elasticsearch/reference/6.0/indices-aliases.html
   */
  public void createOptimizeIndices(RestHighLevelClient esClient) {
    for (TypeMappingCreator mapping : mappings) {
      final String aliasName = getOptimizeIndexAliasForType(mapping.getType());
      final String indexName = getVersionedOptimizeIndexNameForTypeMapping(mapping);
      final Settings indexSettings = createIndexSettings();
      try {
        try {
          CreateIndexRequest request = new CreateIndexRequest(indexName);
          request.alias(new Alias(aliasName));
          request.settings(indexSettings);
          request.mapping(mapping.getType(), mapping.getSource());
          esClient.indices().create(request, RequestOptions.DEFAULT);
        } catch (ElasticsearchStatusException e) {
          if (e.status() == RestStatus.BAD_REQUEST && e.getMessage().contains("resource_already_exists_exception")) {
            logger.debug("index {} already exists, updating mapping and dynamic settings.", indexName);
            updateIndexDynamicSettingsAndMappings(esClient, mapping);
          } else {
            throw e;
          }
        }
      } catch (Exception e) {
        String message = String.format("Could not create Index [%s]", indexName);
        logger.warn(message, e);
        throw new OptimizeRuntimeException(message, e);
      }
    }

    RefreshRequest refreshAllIndexesRequest = new RefreshRequest();
    try {
      esClient.indices().refresh(refreshAllIndexesRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Could not refresh Optimize indices!", e);
    }

    disableAutomaticIndexCreation(esClient);
  }


  private void updateAllMappingsAndDynamicSettings(RestHighLevelClient esClient) {
    logger.info("Updating Optimize schema...");
    for (TypeMappingCreator mapping : mappings) {
      updateIndexDynamicSettingsAndMappings(esClient, mapping);
    }
    logger.info("Finished updating Optimize schema.");
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
      logger.error("Could not retrieve index settings!", e);
      throw new OptimizeRuntimeException("Could not retrieve index settings!", e);
    }
    boolean indexBlocked = false;
    for (Map.Entry<String, Map> entry : responseBodyAsMap.entrySet()) {
      Map<String, Map> settingsMap = (Map) entry.getValue().get("settings");
      Map<String, String> indexSettingsMap = settingsMap.get("index");
      if (Boolean.parseBoolean(indexSettingsMap.get(INDEX_READ_ONLY_SETTING))
        && entry.getKey().contains(OPTIMIZE_INDEX_PREFIX)) {
        indexBlocked = true;
        logger.info("Found blocked Optimize Elasticsearch indices");
        break;
      }
    }

    if (indexBlocked) {
      logger.info("Unblocking Elasticsearch indices...");
      UpdateSettingsRequest updateSettingsRequest = new UpdateSettingsRequest(OPTIMIZE_INDEX_PREFIX + "*");
      updateSettingsRequest.settings(Settings.builder().put(INDEX_READ_ONLY_SETTING, false));
      try {
        esClient.indices().putSettings(updateSettingsRequest, RequestOptions.DEFAULT);
      } catch (IOException e) {
        throw new OptimizeRuntimeException("Could not unblock Elasticsearch indices!", e);
      }
    }
  }

  private void disableAutomaticIndexCreation(RestHighLevelClient esClient) {
    Settings settings = Settings.builder()
      .put("action.auto_create_index", false)
      .build();
    ClusterUpdateSettingsRequest clusterUpdateSettingsRequest = new ClusterUpdateSettingsRequest();
    clusterUpdateSettingsRequest.persistentSettings(settings);
    try (XContentBuilder builder = XContentFactory.jsonBuilder()) {
      // low level request as we need body serialized with flat_settings option for AWS hosted elasticsearch support
      Request request = new Request("PUT", "/_cluster/settings");
      request.setJsonEntity(Strings.toString(
        clusterUpdateSettingsRequest.toXContent(builder, XCONTENT_PARAMS_FLAT_SETTINGS)
      ));
      esClient.getLowLevelClient().performRequest(request);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Could not update index settings!", e);
    }
  }

  private void updateIndexDynamicSettingsAndMappings(RestHighLevelClient esClient, TypeMappingCreator typeMapping) {
    final String indexName = getVersionedOptimizeIndexNameForTypeMapping(typeMapping);
    try {
      final Settings indexSettings = buildDynamicSettings(configurationService);
      final UpdateSettingsRequest updateSettingsRequest = new UpdateSettingsRequest();
      updateSettingsRequest.indices(indexName);
      updateSettingsRequest.settings(indexSettings);
      esClient.indices().putSettings(updateSettingsRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String message = String.format("Could not update index settings for type [%s].", typeMapping.getType());
      throw new OptimizeRuntimeException(message, e);
    }

    try {
      final PutMappingRequest putMappingRequest = new PutMappingRequest(indexName);
      putMappingRequest.type(typeMapping.getType()).source(typeMapping.getSource());
      esClient.indices().putMapping(putMappingRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String message = String.format("Could not update index mappings for type [%s].", typeMapping.getType());
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private Settings createIndexSettings() {
    try {
      return IndexSettingsBuilder.buildAllSettings(configurationService);
    } catch (IOException e) {
      logger.error("Could not create settings!", e);
      throw new OptimizeRuntimeException("Could not create index settings");
    }
  }

}
