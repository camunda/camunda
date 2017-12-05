package org.camunda.optimize.service.es.schema;

import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.ResourceAlreadyExistsException;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

@Component
public class ElasticSearchSchemaManager {

  private Logger logger = LoggerFactory.getLogger(ElasticSearchSchemaManager.class);
  private Client esclient;
  private ConfigurationService configurationService;

  private List<TypeMappingCreator> mappings = new LinkedList<>();

  public void addMapping(TypeMappingCreator mapping) {
    mappings.add(mapping);
  }

  public boolean schemaAlreadyExists() {
    String[] optimizeIndex = new String [mappings.size()];
    int i = 0;
    for (TypeMappingCreator creator : mappings) {
      optimizeIndex[i] = configurationService.getOptimizeIndex(creator.getType());
      i = ++i;
    }
    IndicesExistsResponse response = esclient
      .admin()
      .indices()
      .prepareExists(optimizeIndex)
      .get();
    return response.isExists();
  }


  /**
   * NOTE: create one index per type
   *
   * https://www.elastic.co/guide/en/elasticsearch/reference/current/removal-of-types.html
   *
   */
  public void createOptimizeIndex() {
    Settings indexSettings = null;
    for (TypeMappingCreator mapping : mappings) {
      try {
        indexSettings = buildSettings();
      } catch (IOException e) {
        logger.error("Could not create settings!", e);
      }
      try {
        CreateIndexRequestBuilder createIndexRequestBuilder = esclient
            .admin()
            .indices()
            .prepareCreate(configurationService.getOptimizeIndex(mapping.getType()))
            .setSettings(indexSettings);

        createIndexRequestBuilder = createIndexRequestBuilder.addMapping(
            mapping.getType(),
            mapping.getSource()
        );
        createIndexRequestBuilder
            .get();
      } catch (ResourceAlreadyExistsException e) {
        logger.warn("Index for type [{}] already exists", mapping.getType());
      }

      esclient.admin().indices().prepareRefresh().get();
    }
  }

  public void updateMappings() {
    for (TypeMappingCreator mapping : mappings) {
      createSingleSchema(mapping.getType(), mapping.getSource());
    }
  }

  private void createSingleSchema(String type, XContentBuilder content) {
    esclient
        .admin()
        .indices()
        .preparePutMapping(configurationService.getOptimizeIndex(type))
        .setType(type)
        .setSource(content)
        .get();
  }

  private Settings buildSettings() throws IOException {
    return Settings.builder()
      .put("index.mapper.dynamic", false)
      .loadFromSource(jsonBuilder()
      .startObject()
        .field("index.mapper.dynamic", false)
        .field("refresh_interval", configurationService.getEsRefreshInterval())
        .field("number_of_replicas", configurationService.getEsNumberOfReplicas())
        .field("number_of_shards", configurationService.getEsNumberOfShards())
        .startObject("analysis")
          .startObject("analyzer")
            .startObject(configurationService.getAnalyzerName())
              .field("type", "custom")
              .field("tokenizer", configurationService.getTokenizer())
              .field("filter", new String[]{configurationService.getTokenFilter()})
            .endObject()
        . endObject()
        .endObject()
      .endObject()
      .string(), XContentType.JSON)
    .build();
  }

  public Client getEsclient() {
    return esclient;
  }

  public void setEsclient(Client esclient) {
    this.esclient = esclient;
  }

  public ConfigurationService getConfigurationService() {
    return configurationService;
  }

  public void setConfigurationService(ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }
}
