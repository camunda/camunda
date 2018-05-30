package org.camunda.optimize.upgrade.es;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.wrapper.DestinationWrapper;
import org.camunda.optimize.upgrade.wrapper.ReindexPayload;
import org.camunda.optimize.upgrade.wrapper.ScriptWrapper;
import org.camunda.optimize.upgrade.wrapper.SourceWrapper;
import org.camunda.optimize.upgrade.util.SchemaUpgradeUtil;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ESIndexAdjuster {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  private static final String PUT = "PUT";
  private static final String GET = "GET";
  private static final String POST = "POST";
  private static final String DELETE = "DELETE";

  private static final String TASKS_ENDPOINT = "_tasks";
  private static final String REINDEX_OPERATION = "reindex";
  private static final String UPDATE_BY_QUERY_OPERATION = "/_update_by_query";

  private static final String TYPE_KEY = "type";
  private static final String DATE_TYPE = "date";
  private static final String FORMAT_KEY = "format";

  private static final String TEMP_SUFFIX = "-temp";
  private static final int ONE_SECOND = 1000;
  private final RestClient restClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private final String dateFormat;

  public ESIndexAdjuster(RestClient restClient) {
    this(restClient, null);
  }

  public ESIndexAdjuster(RestClient restClient, String dateFormat) {
    this.dateFormat = dateFormat;
    this.restClient = restClient;
  }

  public void reindex(String sourceIndex, String destinationIndex) {
    this.reindex(sourceIndex, destinationIndex, null);
  }

  public void deleteIndex(String indexName) {
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

  public void reindex(String sourceIndex, String destinationIndex, String mappingScript) {
    logger.debug("Reindexing from index [{}] to [{}] using the mapping script [{}].",
      sourceIndex,
      destinationIndex,
      mappingScript);
    ReindexPayload toSend = new ReindexPayload();
    toSend.setSource(new SourceWrapper(sourceIndex));
    toSend.setDest(new DestinationWrapper(destinationIndex));
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
        String.format("Could not reindex data from index [%s] to [%s]!",
          sourceIndex,
          destinationIndex
        );
      throw new UpgradeRuntimeException(errorMessage, e);
    }

    if (reindexResponse.getStatusLine().getStatusCode() == 200) {
      waitUntilReindexingIsFinished();
    } else {
      String errorMessage =
        String.format("Could not reindex data from index [%s] to [%s]! Reindex request was not successful!",
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
      Response response = null;
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


  protected Map<String, String> getParamsWithRefresh() {
    HashMap<String, String> reindexParams = new HashMap<>();
    reindexParams.put("refresh", "true");
    return reindexParams;
  }

  private String getReindexEndpoint() {
    return "_" + REINDEX_OPERATION;
  }

  public void createIndex(String indexName, String mappingAndSettings) {
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

  public String getTempIndexName(String initialIndexName) {
    return initialIndexName + TEMP_SUFFIX;
  }

  public String preProcess(String mappingAndSettings) {
    String dateEnhanced = setDates(mappingAndSettings);
    String result = enhanceWithDefaults(dateEnhanced);
    return result;
  }

  protected String setDates(String mappingAndSettings) {
    String result = mappingAndSettings;
    try {
      HashMap<String, Object> level =
        objectMapper.readValue(mappingAndSettings, new TypeReference<HashMap <String, Object>>() {});
      for (Map.Entry<String, Object> e : level.entrySet()) {
        Object value = e.getValue();
        processValue(value);
      }
      result = objectMapper.writeValueAsString(level);
    } catch (IOException e) {
      logger.error("error while processing date types", e);
    }
    return result;
  }

  public void processValue(Object value) {
    if (value instanceof Map) {
      enhanceNonMapNodes((HashMap<String, Object>) value);
    } else if (value instanceof List) {
      List castValue = (List) value;
      for (Object o : castValue) {
        processValue(o);
      }
    }
  }

  protected void enhanceNonMapNodes(HashMap<String, Object> level) {
    if (
      level.containsKey(TYPE_KEY) && DATE_TYPE.equals(level.get(TYPE_KEY)) &&
        !level.containsKey(FORMAT_KEY) && this.dateFormat != null
      ) {
      level.put(FORMAT_KEY, dateFormat);
    } else {
      for (Map.Entry entry : level.entrySet()) {
        Object value = entry.getValue();
        processValue(value);
      }
    }
  }

  protected String enhanceWithDefaults(String mappingAndSettings) {
    String result = mappingAndSettings;
    try {
      HashMap mapping = objectMapper.readValue(mappingAndSettings, HashMap.class);
      HashMap settings = objectMapper.readValue(
        SchemaUpgradeUtil.readClasspathFileAsString("defaults/settings.json"),
        HashMap.class
      );

      HashMap dynamics = objectMapper.readValue(
        SchemaUpgradeUtil.readClasspathFileAsString("defaults/dynamic.json"),
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

  public void insertData(String indexName, String type, String data)  {
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

  public void updateData(String indexName, String updateScript, String query) {
    logger.debug("Updating data for index [{}] using script [{}] and query [{}].",
      indexName,
      updateScript,
      query);

    try {
      HashMap <String, Object> data = new HashMap<>();
      ScriptWrapper scriptWrapper = new ScriptWrapper(updateScript);

      data.put("script", objectMapper.convertValue(scriptWrapper, new TypeReference<HashMap <String, Object>>() {}));
      data.putAll(objectMapper.readValue(query, new TypeReference<HashMap <String, Object>>() {}));

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
