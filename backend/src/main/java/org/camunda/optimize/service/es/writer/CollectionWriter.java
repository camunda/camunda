package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.service.es.schema.type.CollectionType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.engine.DocumentMissingException;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.UpdateByQueryAction;
import org.elasticsearch.index.reindex.UpdateByQueryRequestBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COLLECTION_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CREATE_SUCCESSFUL_RESPONSE_RESULT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DELETE_SUCCESSFUL_RESPONSE_RESULT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_REPORT_TYPE;


@Component
public class CollectionWriter {

  private static final String DEFAULT_COLLECTION_NAME = "New Collection";
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private Client esclient;
  private ConfigurationService configurationService;
  private ObjectMapper objectMapper;

  @Autowired
  public CollectionWriter(Client esclient, ConfigurationService configurationService, ObjectMapper objectMapper) {
    this.esclient = esclient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
  }

  public IdDto createNewCollectionAndReturnId(String userId) {
    logger.debug("Writing new collection to Elasticsearch");

    String id = IdGenerator.getNextId();

    SimpleCollectionDefinitionDto collection = new SimpleCollectionDefinitionDto();
    collection.setId(id);
    collection.setCreated(LocalDateUtil.getCurrentDateTime());
    collection.setLastModified(LocalDateUtil.getCurrentDateTime());
    collection.setOwner(userId);
    collection.setLastModifier(userId);
    collection.setName(DEFAULT_COLLECTION_NAME);

    try {
      IndexResponse indexResponse = esclient
        .prepareIndex(getOptimizeIndexAliasForType(COLLECTION_TYPE), COLLECTION_TYPE, id)
        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
        .setSource(objectMapper.writeValueAsString(collection), XContentType.JSON)
        .get();

      if (!indexResponse.getResult().getLowercase().equals(CREATE_SUCCESSFUL_RESPONSE_RESULT)) {
        String message = "Could not write collection to Elasticsearch. " +
          "Maybe the connection to Elasticsearch got lost?";
        logger.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (JsonProcessingException e) {
      String errorMessage = "Could not create collection.";
      logger.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    logger.debug("Collection with id [{}] has successfully been created.", id);
    IdDto idDto = new IdDto();
    idDto.setId(id);
    return idDto;
  }

  public void updateCollection(CollectionDefinitionUpdateDto collection, String id) {
    logger.debug("Updating collection with id [{}] in Elasticsearch", id);

    ensureThatAllProvidedReportIdsExist(collection.getData());
    try {
      UpdateResponse updateResponse = esclient
        .prepareUpdate(getOptimizeIndexAliasForType(COLLECTION_TYPE), COLLECTION_TYPE, id)
        .setDoc(objectMapper.writeValueAsString(collection), XContentType.JSON)
        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
        .setRetryOnConflict(configurationService.getNumberOfRetriesOnConflict())
        .get();

      if (updateResponse.getShardInfo().getFailed() > 0) {
        logger.error(
          "Was not able to update collection with id [{}] and name [{}].",
          id,
          collection.getName()
        );
        throw new OptimizeRuntimeException("Was not able to update collection!");
      }
    } catch (JsonProcessingException e) {
      String errorMessage = String.format(
        "Was not able to update collection with id [%s] and name [%s]. Could not serialize collection update!",
        id,
        collection.getName()
      );
      logger.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (DocumentMissingException e) {
      String errorMessage = String.format(
        "Was not able to update collection with id [%s] and name [%s]. Collection does not exist!",
        id,
        collection.getName()
      );
      logger.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }

  private void ensureThatAllProvidedReportIdsExist(CollectionDataDto<String> collectionData) {
    boolean reportIdsAreProvided =
      collectionData != null && collectionData.getEntities() != null && !collectionData.getEntities()
        .isEmpty();
    if (reportIdsAreProvided) {
      List<String> reportIds = collectionData.getEntities();
      logger.debug("Checking that the given report ids [{}] for a collection exist", reportIds);
      SearchResponse searchResponse = esclient.prepareSearch()
        .setIndices(
          getOptimizeIndexAliasForType(SINGLE_REPORT_TYPE),
          getOptimizeIndexAliasForType(COMBINED_REPORT_TYPE)
        )
        .setQuery(QueryBuilders.idsQuery().addIds(reportIds.toArray(new String[0])))
        .setSize(0)
        .get();
      if (searchResponse.getHits().getTotalHits() != reportIds.size()) {
        String errorMessage = "Could not update collection, since the update contains report ids that " +
          "do not exist in Optimize any longer.";
        logger.error(errorMessage);
        throw new OptimizeRuntimeException(errorMessage);
      }
    }
  }

  public void removeReportFromCollections(String reportId) {
    UpdateByQueryRequestBuilder updateByQuery = UpdateByQueryAction.INSTANCE.newRequestBuilder(esclient);
    Script removeReportFromCollectionScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      "ctx._source.data.entities.removeIf(id -> id.equals(params.idToRemove))",
      Collections.singletonMap("idToRemove", reportId)
    );

    updateByQuery.source(getOptimizeIndexAliasForType(COLLECTION_TYPE))
      .abortOnVersionConflict(false)
      .setMaxRetries(configurationService.getNumberOfRetriesOnConflict())
      .filter(
        QueryBuilders.nestedQuery(
          CollectionType.DATA,
          QueryBuilders.termQuery(CollectionType.DATA + "." + CollectionType.ENTITIES, reportId),
          ScoreMode.None
        )
      )
      .script(removeReportFromCollectionScript)
      .refresh(true);

    BulkByScrollResponse response = updateByQuery.get();
    if (!response.getBulkFailures().isEmpty()) {
      String errorMessage =
        String.format(
          "Could not remove report id [%s] from collection! Error response: %s",
          reportId,
          response.getBulkFailures()
        );
      logger.error(errorMessage);
      throw new OptimizeRuntimeException(errorMessage);
    }
  }

  public void deleteCollection(String collectionId) {
    logger.debug("Deleting collection with id [{}]", collectionId);
    DeleteResponse deleteResponse = esclient.prepareDelete(
      getOptimizeIndexAliasForType(COLLECTION_TYPE),
      COLLECTION_TYPE,
      collectionId
    )
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .get();

    if (!deleteResponse.getResult().getLowercase().equals(DELETE_SUCCESSFUL_RESPONSE_RESULT)) {
      String message = String.format("Could not delete collection with id [%s]. Collection does not exist." +
                                       "Maybe it was already deleted by someone else?", collectionId);
      logger.error(message);
      throw new NotFoundException(message);
    }
  }

}
