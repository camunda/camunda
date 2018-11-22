package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedReportCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.CollectionType.DATA;
import static org.camunda.optimize.service.es.schema.type.CollectionType.NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COLLECTION_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;

@Component
public class CollectionReader {
  private static final Logger logger = LoggerFactory.getLogger(CollectionReader.class);
  public static final String EVERYTHING_ELSE_COLLECTION_ID = "everythingElse";
  public static final String EVERYTHING_ELSE_COLLECTION_NAME = "Everything Else";

  private final Client esclient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;
  private final ReportReader reportReader;

  @Autowired
  public CollectionReader(final Client esclient, final ConfigurationService configurationService,
                          final ObjectMapper objectMapper,
                          final ReportReader reportReader) {
    this.esclient = esclient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
    this.reportReader = reportReader;
  }

  public SimpleCollectionDefinitionDto getCollection(String collectionId) {
    logger.debug("Fetching collection with id [{}]", collectionId);
    GetResponse getResponse = esclient
      .prepareGet(
        getOptimizeIndexAliasForType(COLLECTION_TYPE), COLLECTION_TYPE, collectionId
      )
      .setRealtime(false)
      .get();

    if (getResponse.isExists()) {
      String responseAsString = getResponse.getSourceAsString();
      try {
        return objectMapper.readValue(responseAsString, SimpleCollectionDefinitionDto.class);
      } catch (IOException e) {
        String reason = "Could not deserialize collection information for collection " + collectionId;
        logger.error("Was not able to retrieve collection with id [{}] from Elasticsearch. Reason: reason");
        throw new OptimizeRuntimeException(reason, e);
      }
    } else {
      logger.error("Was not able to retrieve collection with id [{}] from Elasticsearch.", collectionId);
      throw new NotFoundException("Collection does not exist! Tried to retried collection with id " + collectionId);
    }
  }

  public List<ResolvedReportCollectionDefinitionDto> getAllResolvedReportCollections() {
    List<SimpleCollectionDefinitionDto> allCollections = getAllCollections();
    Set<String> reportIds = getAllReportIdsFromCollections(allCollections);

    final Map<String, ReportDefinitionDto> reportIdToReportMap = reportReader.getAllReports()
      .stream()
      .collect(toMap(ReportDefinitionDto::getId, r -> r));

    SimpleCollectionDefinitionDto everythingElseCollection = createEverythingElseCollection(
      reportIds,
      reportIdToReportMap
    );
    allCollections.add(everythingElseCollection);

    logger.debug("Mapping all available report collections to resolved report collections.");
    return allCollections.stream()
      .map(c -> mapToResolvedCollection(c, reportIdToReportMap))
      .collect(Collectors.toList());
  }

  /**
   * This function creates a new collection containing all reports that do not belong
   * to any collection the user defined.
   */
  private SimpleCollectionDefinitionDto createEverythingElseCollection(Set<String> collectionReportIds,
                                                                       Map<String, ReportDefinitionDto> reportIdToReportMap) {
    SimpleCollectionDefinitionDto everythingElseCollection = new SimpleCollectionDefinitionDto();
    everythingElseCollection.setId(EVERYTHING_ELSE_COLLECTION_ID);
    everythingElseCollection.setName(EVERYTHING_ELSE_COLLECTION_NAME);

    CollectionDataDto<String> emptyCollectionData = new CollectionDataDto<>();
    emptyCollectionData.setConfiguration(new HashMap<>());
    everythingElseCollection.setData(emptyCollectionData);

    Set<String> allReportsIdsWithoutCollection = reportIdToReportMap.keySet()
      .stream()
      .filter(s -> !collectionReportIds.contains(s))
      .collect(Collectors.toSet());

    emptyCollectionData.setEntities(new ArrayList<>(allReportsIdsWithoutCollection));
    everythingElseCollection.setLastModified(OffsetDateTime.now());

    return everythingElseCollection;
  }


  private Set<String> getAllReportIdsFromCollections(List<SimpleCollectionDefinitionDto> allCollections) {
    return allCollections.stream()
      .map(SimpleCollectionDefinitionDto::getData)
      .filter(Objects::nonNull)
      .map(CollectionDataDto::getEntities)
      .flatMap(List::stream)
      .collect(Collectors.toSet());
  }

  private ResolvedReportCollectionDefinitionDto mapToResolvedCollection(SimpleCollectionDefinitionDto c,
                                                                        Map<String, ReportDefinitionDto> reportIdToReportMap) {
    ResolvedReportCollectionDefinitionDto resolvedReportCollection =
      new ResolvedReportCollectionDefinitionDto();
    resolvedReportCollection.setId(c.getId());
    resolvedReportCollection.setName(c.getName());
    resolvedReportCollection.setLastModifier(c.getLastModifier());
    resolvedReportCollection.setOwner(c.getOwner());
    resolvedReportCollection.setCreated(c.getCreated());
    resolvedReportCollection.setLastModified(c.getLastModified());

    if (c.getData() != null) {
      CollectionDataDto<String> collectionData = c.getData();
      CollectionDataDto<ReportDefinitionDto> resolvedCollectionData = new CollectionDataDto<>();
      resolvedCollectionData.setConfiguration(c.getData().getConfiguration());
      resolvedCollectionData.setEntities(
        collectionData.getEntities()
          .stream()
          .map(reportIdToReportMap::get)
          .filter(Objects::nonNull)
          .collect(Collectors.toList())
      );
      resolvedReportCollection.setData(resolvedCollectionData);
    }
    return resolvedReportCollection;
  }

  private List<SimpleCollectionDefinitionDto> getAllCollections() {
    logger.debug("Fetching all available collections");
    SearchResponse scrollResp = esclient
      .prepareSearch(getOptimizeIndexAliasForType(COLLECTION_TYPE))
      .setTypes(COLLECTION_TYPE)
      .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
      .setQuery(QueryBuilders.matchAllQuery())
      .addSort(NAME, SortOrder.ASC)
      .setSize(LIST_FETCH_LIMIT)
      .get();

    return ElasticsearchHelper.retrieveAllScrollResults(
      scrollResp,
      SimpleCollectionDefinitionDto.class,
      objectMapper,
      esclient,
      configurationService.getElasticsearchScrollTimeout()
    );
  }

  public List<SimpleCollectionDefinitionDto> findFirstCollectionsForReport(String reportId) {
    logger.debug("Fetching collections using report with id {}", reportId);

    final QueryBuilder getCombinedReportsBySimpleReportIdQuery = QueryBuilders.boolQuery()
      .filter(QueryBuilders.nestedQuery(
        DATA,
        QueryBuilders.termQuery("data.entities", reportId),
        ScoreMode.None
      ));

    SearchResponse searchResponse = esclient
      .prepareSearch(getOptimizeIndexAliasForType(COLLECTION_TYPE))
      .setTypes(COLLECTION_TYPE)
      .setQuery(getCombinedReportsBySimpleReportIdQuery)
      .setSize(LIST_FETCH_LIMIT)
      .get();

    return ElasticsearchHelper.mapHits(searchResponse.getHits(), SimpleCollectionDefinitionDto.class, objectMapper);
  }
}
