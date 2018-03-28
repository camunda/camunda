package org.camunda.optimize.upgrade.executor;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.upgrade.to.DestinationWrapper;
import org.camunda.optimize.upgrade.to.ReindexPayload;
import org.camunda.optimize.upgrade.to.ScriptWrapper;
import org.camunda.optimize.upgrade.to.SourceWrapper;
import org.camunda.optimize.upgrade.util.SchemaUpgradeUtil;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Askar Akhmerov
 */
public abstract class AbstractRESTExecutor {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  protected static final String PUT = "PUT";
  protected static final String REINDEX_OPERATION = "reindex";
  protected static final String GET = "GET";
  protected static final String TASKS_ENDPOINT = "_tasks";
  protected static final String POST = "POST";
  protected static final String DELETE = "DELETE";

  private static final String TYPE_KEY = "type";
  private static final String DATE_TYPE = "date";
  private static final String FORMAT_KEY = "format";

  protected static final String TEMP_SUFFIX = "-temp";
  protected static final int ONE_SECOND = 1000;
  protected final RestClient restClient;
  protected final ObjectMapper objectMapper = new ObjectMapper();

  protected final String dateFormat;

  public AbstractRESTExecutor(RestClient restClient) {
    this(restClient, null);
  }

  public AbstractRESTExecutor(RestClient restClient, String dateFormat) {
    this.dateFormat = dateFormat;
    this.restClient = restClient;
  }

  protected void reindex(String sourceIndex, String destinationIndex) throws Exception {
    this.reindex(sourceIndex, destinationIndex, null);
  }

  protected void deleteIndex(String indexName) throws Exception {
    restClient.performRequest(DELETE, indexName);
  }

  protected void reindex(String sourceIndex, String destinationIndex, String mappingScript) throws Exception {
    ReindexPayload toSend = new ReindexPayload();
    toSend.setSource(new SourceWrapper(sourceIndex));
    toSend.setDest(new DestinationWrapper(destinationIndex));
    if (mappingScript != null) {
      toSend.setScript(new ScriptWrapper(mappingScript));
    }
    ObjectMapper om = new ObjectMapper();
    om.setSerializationInclusion(JsonInclude.Include.NON_NULL);

    HttpEntity entity = new NStringEntity(om.writeValueAsString(toSend), ContentType.APPLICATION_JSON);

    Response reindexResponse = restClient.performRequest(POST, getReindexEndpoint(), getParamsWithRefresh(), entity);

    if (reindexResponse.getStatusLine().getStatusCode() == 200) {
      boolean finished = false;

      Map<String, String> params = new HashMap<>();
      params.put("detailed", "false");
      params.put("actions", "*" + REINDEX_OPERATION);

      while (!finished) {
        Response response = restClient.performRequest(GET, TASKS_ENDPOINT, params);
        String stringResponse = EntityUtils.toString(response.getEntity());
        if (!stringResponse.contains(REINDEX_OPERATION)) {
          finished = true;
        } else {
          Thread.sleep(ONE_SECOND);
        }
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

  protected void createIndex(String indexName, String mappingAndSettings) throws IOException {
    HttpEntity entity = new NStringEntity(preProcess(mappingAndSettings), ContentType.APPLICATION_JSON);
    restClient.performRequest(PUT, indexName, new HashMap<>(), entity);
  }

  protected String getTempIndexName(String initialIndexName) {
    return initialIndexName + TEMP_SUFFIX;
  }

  protected String preProcess(String mappingAndSettings) {
    String dateEnhanced = setDates(mappingAndSettings);
    String result = enhanceWithDefaults(dateEnhanced);
    return result;
  }

  protected String setDates(String mappingAndSettings) {
    String result = mappingAndSettings;
    try {
      HashMap<String, Object> level = objectMapper.readValue(mappingAndSettings, HashMap.class);
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

  protected void processValue(Object value) {
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
}
