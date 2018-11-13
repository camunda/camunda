package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;

@Component
public class ReportReader {
  private static final Logger logger = LoggerFactory.getLogger(ReportReader.class);

  @Autowired
  private Client esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  /**
   * Obtain report by it's ID from elasticsearch
   *
   * @param reportId - id of report, expected not null
   * @throws OptimizeRuntimeException if report with specified ID does not
   *                                  exist or deserialization was not successful.
   */
  public ReportDefinitionDto getReport(String reportId) {
    logger.debug("Fetching report with id [{}]", reportId);
    MultiGetResponse multiGetItemResponses = esclient.prepareMultiGet()
      .add(
        getOptimizeIndexAliasForType(ElasticsearchConstants.SINGLE_REPORT_TYPE),
        ElasticsearchConstants.SINGLE_REPORT_TYPE, reportId
      )
      .add(
        getOptimizeIndexAliasForType(ElasticsearchConstants.COMBINED_REPORT_TYPE),
        ElasticsearchConstants.COMBINED_REPORT_TYPE, reportId
      )
      .setRealtime(false)
      .get();

    Optional<ReportDefinitionDto> result = Optional.empty();
    for (MultiGetItemResponse itemResponse : multiGetItemResponses) {
      GetResponse response = itemResponse.getResponse();
      Optional<ReportDefinitionDto> reportDefinitionDto = processGetReportResponse(reportId, response);
      if (reportDefinitionDto.isPresent()) {
        result = reportDefinitionDto;
        break;
      }
    }

    if (!result.isPresent()) {
      String reason = "Was not able to retrieve report with id [" + reportId +
        "] from Elasticsearch. Report does not exist.";
      logger.error(reason);
      throw new NotFoundException(reason);
    }
    return result.get();
  }

  public List<ReportDefinitionDto> getAllReports() {
    logger.debug("Fetching all available reports");
    SearchResponse scrollResp = esclient
      .prepareSearch(
        getOptimizeIndexAliasForType(ElasticsearchConstants.SINGLE_REPORT_TYPE),
        getOptimizeIndexAliasForType(ElasticsearchConstants.COMBINED_REPORT_TYPE)
      )
      .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
      .setQuery(QueryBuilders.matchAllQuery())
      .setSize(LIST_FETCH_LIMIT)
      .get();

    return ElasticsearchHelper.retrieveAllScrollResults(
      scrollResp,
      ReportDefinitionDto.class,
      objectMapper,
      esclient,
      configurationService.getElasticsearchScrollTimeout()
    );
  }

  public List<CombinedReportDefinitionDto> findFirstCombinedReportsForSimpleReport(String simpleReportId) {
    logger.debug("Fetching first combined reports using simpleReport with id {}", simpleReportId);

    final QueryBuilder getCombinedReportsBySimpleReportIdQuery = QueryBuilders.boolQuery()
      .filter(QueryBuilders.nestedQuery(
        "data",
        QueryBuilders.termQuery("data.reportIds", simpleReportId),
        ScoreMode.None
      ));

    SearchResponse searchResponse = esclient
      .prepareSearch(getOptimizeIndexAliasForType(ElasticsearchConstants.COMBINED_REPORT_TYPE))
      .setQuery(getCombinedReportsBySimpleReportIdQuery)
      .setSize(LIST_FETCH_LIMIT)
      .get();

    return ElasticsearchHelper.mapHits(searchResponse.getHits(), CombinedReportDefinitionDto.class, objectMapper);
  }

  private Optional<ReportDefinitionDto> processGetReportResponse(String reportId, GetResponse getResponse) {
    Optional<ReportDefinitionDto> result = Optional.empty();
    if (getResponse.isExists()) {
      String responseAsString = getResponse.getSourceAsString();
      try {
        ReportDefinitionDto report = objectMapper.readValue(responseAsString, ReportDefinitionDto.class);
        result = Optional.of(report);
      } catch (IOException e) {
        String reason = "While retrieving report with id [" + reportId +
          "] could not deserialize report from Elasticsearch!";
        logger.error(reason, e);
        throw new OptimizeRuntimeException(reason);
      }
    }
    return result;

  }

}
