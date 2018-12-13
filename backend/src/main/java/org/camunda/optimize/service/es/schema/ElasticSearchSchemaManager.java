package org.camunda.optimize.service.es.schema;

import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.ResourceAlreadyExistsException;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getVersionedOptimizeIndexNameForTypeMapping;

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
    String[] optimizeIndex = new String[mappings.size()];
    int i = 0;
    for (TypeMappingCreator creator : mappings) {
      optimizeIndex[i] = getOptimizeIndexAliasForType(creator.getType());
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
   * NOTE: create one alias and index per type
   * <p>
   * https://www.elastic.co/guide/en/elasticsearch/reference/6.0/indices-aliases.html
   */
  public void createOptimizeIndex() {
    Settings indexSettings = null;
    for (TypeMappingCreator mapping : mappings) {
      try {
        indexSettings = IndexSettingsBuilder.build(configurationService);
      } catch (IOException e) {
        logger.error("Could not create settings!", e);
      }
      try {
        final String optimizeAliasForType = getOptimizeIndexAliasForType(mapping.getType());
        CreateIndexRequestBuilder createIndexRequestBuilder = esclient
          .admin()
          .indices()
          .prepareCreate(getVersionedOptimizeIndexNameForTypeMapping(mapping))
          .addAlias(new Alias(optimizeAliasForType))
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
    disableAutomaticIndexCreation();
  }

  private void disableAutomaticIndexCreation() {
    Settings settings =
      Settings.builder()
        .put("action.auto_create_index", false)
        .build();
    esclient
      .admin()
      .cluster()
      .prepareUpdateSettings()
      .setPersistentSettings(settings)
      .get();
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
      .preparePutMapping(getOptimizeIndexAliasForType(type))
      .setType(type)
      .setSource(content)
      .get();
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
