package org.camunda.optimize.upgrade.es;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.service.es.schema.DynamicSettingsBuilder;
import org.camunda.optimize.service.es.schema.IndexSettingsBuilder;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.wrapper.DestinationWrapper;
import org.camunda.optimize.upgrade.wrapper.ReindexPayload;
import org.camunda.optimize.upgrade.wrapper.ScriptWrapper;
import org.camunda.optimize.upgrade.wrapper.SourceWrapper;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ESIndexAdjuster {

  private Logger logger = LoggerFactory.getLogger(getClass());

  private static final String PUT = "PUT";
  private static final String GET = "GET";
  private static final String POST = "POST";
  private static final String DELETE = "DELETE";

  private static final String TASKS_ENDPOINT = "_tasks";
  private static final String REINDEX_OPERATION = "reindex";
  private static final String UPDATE_BY_QUERY_OPERATION = "/_update_by_query";
  private static final String MAPPING_OPERATION = "/_mapping";

  private static final String TEMP_SUFFIX = "-temp";
  private static final int ONE_SECOND = 1000;
  private final RestClient restClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private final ConfigurationService configurationService;

  public ESIndexAdjuster(RestClient restClient, ConfigurationService configurationService) {
    this.configurationService = configurationService;
    this.restClient = restClient;
  }

  public void reindex(String sourceTypeToConstructIndexFrom, String destinationTypeToConstructIndexFrom,
                      String sourceType, String destType) {
    this.reindex(
      sourceTypeToConstructIndexFrom,
      destinationTypeToConstructIndexFrom,
      sourceType,
      destType,
      null
    );
  }

  public void deleteIndex(String typeName) {
    String indexName = configurationService.getOptimizeIndex(typeName);
    logger.debug("Deleting index [{}].", indexName);
    try {
      restClient.performRequest(DELETE, indexName);
    } catch (IOException e) {
      String errorMessage =
        String.format("Could not delete index [%s]!",
          indexName
        );
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  public String getIndexMappings(String typeName) {
    String indexName = configurationService.getOptimizeIndex(typeName);
    logger.debug("Retrieve index mapping for index [{}].", indexName);
    try {
      Response response = restClient.performRequest(GET, indexName + MAPPING_OPERATION);
      String mappingWithIndexName = EntityUtils.toString(response.getEntity());
      return extractMappings(indexName, mappingWithIndexName);
    } catch (IOException e) {
      String errorMessage =
        String.format("Could not retrieve index mapping from [%s]!",
          indexName
        );
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  private String extractMappings(String indexName, String mappingWithIndexName) throws JsonProcessingException {
    Map read = JsonPath.parse(mappingWithIndexName).read("$." + indexName);
    return objectMapper.writeValueAsString(read);
  }

  public void reindex(String sourceTypeToConstructIndexFrom, String destinationTypeToConstructIndexFrom,
                      String sourceType, String destType, String mappingScript) {
    String sourceIndex = configurationService.getOptimizeIndex(sourceTypeToConstructIndexFrom);
    String destinationIndex = configurationService.getOptimizeIndex(destinationTypeToConstructIndexFrom);
    logger.debug(
      "Reindexing from index [{}] to [{}] using the mapping script [{}].",
      sourceIndex,
      destinationIndex,
      mappingScript
    );
    ReindexPayload toSend = new ReindexPayload();
    toSend.setSource(new SourceWrapper(sourceIndex, sourceType));
    toSend.setDest(new DestinationWrapper(destinationIndex, destType));
    if (mappingScript != null) {
      toSend.setScript(new ScriptWrapper(mappingScript));
    }
    ObjectMapper om = new ObjectMapper();
    om.setSerializationInclusion(JsonInclude.Include.NON_NULL);


    Response reindexResponse;
    try {
      HttpEntity entity = new NStringEntity(om.writeValueAsString(toSend), ContentType.APPLICATION_JSON);
      reindexResponse = restClient.performRequest(POST, getReindexEndpoint(), getParamsWithRefresh(), entity);
    } catch (IOException e) {
      String errorMessage =
        String.format(
          "Could not reindex data from index [%s] to [%s]!",
          sourceIndex,
          destinationIndex
        );
      throw new UpgradeRuntimeException(errorMessage, e);
    }

    if (reindexResponse.getStatusLine().getStatusCode() == 200) {
      waitUntilReindexingIsFinished();
    } else {
      String errorMessage =
        String.format(
          "Could not reindex data from index [%s] to [%s]! Reindex request was not successful!",
          sourceIndex,
          destinationIndex
        );
      throw new UpgradeRuntimeException(errorMessage);
    }
  }

  private void waitUntilReindexingIsFinished() {
    boolean finished = false;

    Map<String, String> params = new HashMap<>();
    params.put("detailed", "false");
    params.put("actions", "*" + REINDEX_OPERATION);

    while (!finished) {
      Response response;
      try {
        response = restClient.performRequest(GET, TASKS_ENDPOINT, params);
        String stringResponse = EntityUtils.toString(response.getEntity());
        if (!stringResponse.contains(REINDEX_OPERATION)) {
          finished = true;
        } else {
          Thread.sleep(ONE_SECOND);
        }
      } catch (IOException e) {
        String errorMessage =
          "While trying to reindex, could not check progress!";
        throw new UpgradeRuntimeException(errorMessage, e);
      } catch (InterruptedException e) {
        String errorMessage =
          "While trying to reindex, sleeping was interrupted!";
        throw new UpgradeRuntimeException(errorMessage, e);
      }

    }
  }

  private Map<String, String> getParamsWithRefresh() {
    HashMap<String, String> reindexParams = new HashMap<>();
    reindexParams.put("refresh", "true");
    return reindexParams;
  }

  private String getReindexEndpoint() {
    return "_" + REINDEX_OPERATION;
  }

  public void createIndex(String typeName, String mappingAndSettings) {
    String indexName = configurationService.getOptimizeIndex(typeName);
    logger.debug("Creating index [{}] with mapping and settings [{}].",
      indexName,
      mappingAndSettings);

    HttpEntity entity = new NStringEntity(preProcess(mappingAndSettings), ContentType.APPLICATION_JSON);
    try {
      restClient.performRequest(PUT, indexName, new HashMap<>(), entity);
    } catch (IOException e) {
      String errorMessage =
        String.format("Could not create index [%s]!",
          indexName
        );
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  public String getTempTypeName(String initialIndexName) {
    return initialIndexName + TEMP_SUFFIX;
  }

  private String preProcess(String mappingAndSettings) {
    return enhanceWithDefaults(mappingAndSettings);
  }

  private String enhanceWithDefaults(String mappingAndSettings) {
    String result = mappingAndSettings;
    try {
      HashMap mapping = objectMapper.readValue(mappingAndSettings, HashMap.class);
      HashMap settings = objectMapper.readValue(
        IndexSettingsBuilder.buildAsString(configurationService, objectMapper),
        HashMap.class
      );

      HashMap dynamics = objectMapper.readValue(
        buildDynamicSettings(),
        HashMap.class
      );

      mapping.putAll(settings);
      Map<String, Map> mappings = (Map) mapping.get("mappings");
      for (String key : mappings.keySet()) {
        mappings.get(key).putAll(dynamics);
      }

      result = objectMapper.writeValueAsString(mapping);
    } catch (IOException e) {
      logger.error("can't apply defaults to mapping", e);
    }
    return result;
  }

  private String buildDynamicSettings() {
    String dynamicSettings = "";
    try {
      String dynamicSettingsWithPropertyField = DynamicSettingsBuilder.createDynamicSettingsAsString();
      // we need to remove the properties here since they added later with
      // whole schmema information.
      dynamicSettings =
        JsonPath.parse(dynamicSettingsWithPropertyField)
          .delete("$.properties")
          .jsonString();
    } catch (IOException e) {
      logger.error("Can't create default dynamic settings", e);
    }
    return dynamicSettings;
  }

  public void insertData(String type, String data)  {
    String indexName = configurationService.getOptimizeIndex(type);
    logger.debug("Inserting data to index [{}]. Data payload is [{}]", indexName, data);
    HttpEntity entity = new NStringEntity(data, ContentType.APPLICATION_JSON);
    try {
      restClient.performRequest(POST, getEndpointWithId(indexName, type), getParamsWithRefresh(), entity);
    } catch (IOException e) {
      String errorMessage =
        String.format("Could not add data to index [%s]!",
          indexName
        );
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  private String getEndpointWithId(String indexName, String type) {
    return indexName + "/" + type;
  }

  public void updateData(String typeName, QueryBuilder query, String updateScript) {
    String indexName = configurationService.getOptimizeIndex(typeName);
    logger.debug("Updating data for index [{}] using script [{}] and query [{}].",
      indexName,
      updateScript,
      query.toString());

    try {
      HashMap <String, Object> data = new HashMap<>();
      ScriptWrapper scriptWrapper = new ScriptWrapper(updateScript);

      data.put("script", objectMapper.convertValue(scriptWrapper, new TypeReference<HashMap <String, Object>>() {}));
      // we need to wrap the query in a query object in order
      // to be confirm with Elasticsearch query syntax.
      String wrappedQuery = String.format("{ \"query\": %s }", query.toString());
      data.putAll(objectMapper.readValue(wrappedQuery, new TypeReference<HashMap <String, Object>>() {}));

      HttpEntity entity = new NStringEntity(objectMapper.writeValueAsString(data), ContentType.APPLICATION_JSON);
      restClient.performRequest(POST, indexName + UPDATE_BY_QUERY_OPERATION, getParamsWithRefresh(), entity);
    } catch (IOException e) {
      String errorMessage =
        String.format("Could not update data for index [%s]!",
          indexName
        );
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }
}
