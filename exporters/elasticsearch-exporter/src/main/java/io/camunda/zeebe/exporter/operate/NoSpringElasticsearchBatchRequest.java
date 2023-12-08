package io.camunda.zeebe.exporter.operate;

import static io.camunda.operate.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.JacksonConfig;
import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.ElasticsearchProperties;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.ElasticsearchUtil;

public class NoSpringElasticsearchBatchRequest implements BatchRequest {

  private static final Logger logger = LoggerFactory.getLogger(NoSpringElasticsearchBatchRequest.class);

  private final BulkRequest bulkRequest = new BulkRequest();

  private ObjectMapper objectMapper = NoSpringJacksonConfig.buildObjectMapper();

  private RestHighLevelClient esClient;

  public NoSpringElasticsearchBatchRequest(RestHighLevelClient client) {
    this.esClient = client;
  }
  
  @Override
  public BatchRequest add(String index, OperateEntity entity) throws PersistenceException {
    return addWithId(index, entity.getId(), entity);
  }

  @Override
  public BatchRequest addWithId(String index, String id, OperateEntity entity) throws PersistenceException {
    logger.debug("Add index request for index {} id {} and entity {} ", index, id, entity);
    try {
      bulkRequest.add(
          new IndexRequest(index)
              .id(id)
              .source(objectMapper.writeValueAsString(entity), XContentType.JSON));
    } catch (JsonProcessingException e) {
      throw new PersistenceException(String.format("Error preparing the query to index [%s] of entity type [%s] ", entity.getClass().getName(), entity), e);
    }
    return this;
  }

  @Override
  public BatchRequest addWithRouting(String index, OperateEntity entity, String routing)
      throws PersistenceException {
    logger.debug("Add index request with routing {} for index {} and entity {} ", routing, index , entity);
    try {
      bulkRequest.add(
          new IndexRequest(index)
              .id(entity.getId())
              .source(objectMapper.writeValueAsString(entity), XContentType.JSON))
          .routing(routing);
    } catch (JsonProcessingException e) {
      throw new PersistenceException(String.format("Error preparing the query to index [%s] of entity type [%s] with routing", entity.getClass().getName(), entity),e);
    }
    return this;
  }

  @Override
  public BatchRequest upsert(String index, String id, OperateEntity entity, Map<String, Object> updateFields)
      throws PersistenceException {
    logger.debug("Add upsert request for index {} id {} entity {} and update fields {}", index, id, entity, updateFields);
    try {
      bulkRequest.add(new UpdateRequest().index(index).id(id)
          .doc(objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class))   //empty
          .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON));
    } catch (JsonProcessingException e) {
      throw new PersistenceException(String.format("Error preparing the query to upsert [%s] of entity type [%s]", entity.getClass().getName(), entity),e);
    }
    return this;
  }

  @Override
  public BatchRequest upsertWithRouting(String index, String id, OperateEntity entity, Map<String, Object> updateFields, String routing)
      throws PersistenceException {
    logger.debug("Add upsert request with routing {} for index {} id {} entity {} and update fields ", routing, index, id, entity, updateFields);
    try {
      bulkRequest.add(
          new UpdateRequest().index(index).id(id)
              .doc(objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class))
          .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
          .routing(routing)
          .retryOnConflict(UPDATE_RETRY_COUNT)
      );
    } catch (JsonProcessingException e) {
      throw new PersistenceException(String.format("Error preparing the query to upsert [%s] of entity type [%s] with routing", entity.getClass().getName(), entity),e);
    }
    return this;
  }

  @Override
  public BatchRequest update(String index, String id, Map<String, Object> updateFields) throws PersistenceException {
    logger.debug("Add update request for index {} id {} and update fields {}", index, id, updateFields);
    try {
      bulkRequest.add(new UpdateRequest()
          .index(index)
          .id(id)
          .doc(objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class))
          .retryOnConflict(UPDATE_RETRY_COUNT));
    } catch (JsonProcessingException e) {
      throw new PersistenceException(String.format("Error preparing the query to update index [%s] document with id [%s]", index, id),e);
    }
    return this;
  }

  @Override
  public BatchRequest update(String index, String id, OperateEntity entity) throws PersistenceException {
    try {
      return update(index, id, objectMapper.readValue(objectMapper.writeValueAsString(entity), HashMap.class));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
  @Override
  public BatchRequest updateWithScript(String index, String id, String script, Map<String, Object> parameters) throws PersistenceException {
    logger.debug("Add update with script request for index {} id {} ", index, id);
    final UpdateRequest updateRequest = new UpdateRequest()
        .index(index)
        .id(id)
        .script(getScriptWithParameters(script, parameters))
        .retryOnConflict(UPDATE_RETRY_COUNT);
      bulkRequest.add(updateRequest);
      return this;
  }

  private Script getScriptWithParameters(String script, Map<String, Object> parameters) throws PersistenceException {
    try {
      return new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, script, objectMapper.readValue(objectMapper.writeValueAsString(parameters), HashMap.class));
    } catch (IOException e) {
      throw new PersistenceException(e);
    }
  }

  @Override
  public void execute() throws PersistenceException{
    logger.debug("Execute batchRequest with {} requests", bulkRequest.requests().size());
    ElasticsearchUtil.processBulkRequest(esClient, bulkRequest, ElasticsearchProperties.BULK_REQUEST_MAX_SIZE_IN_BYTES_DEFAULT);
  }

  @Override
  public void executeWithRefresh() throws PersistenceException {
    logger.debug("Execute batchRequest with {} requests and refresh", bulkRequest.requests().size());
    ElasticsearchUtil.processBulkRequest(esClient, bulkRequest, true, ElasticsearchProperties.BULK_REQUEST_MAX_SIZE_IN_BYTES_DEFAULT);
  }

}

