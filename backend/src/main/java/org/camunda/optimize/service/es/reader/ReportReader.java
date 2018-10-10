package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.service.es.schema.type.SingleReportType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ReportReader {

  private final Logger logger = LoggerFactory.getLogger(getClass());

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
   * exist or deserialization was not successful.
   */
  public ReportDefinitionDto getReport(String reportId) {
    logger.debug("Fetching report with id [{}]", reportId);
    MultiGetResponse multiGetItemResponses = esclient.prepareMultiGet()
    .add(
      configurationService.getOptimizeIndex(ElasticsearchConstants.SINGLE_REPORT_TYPE),
      ElasticsearchConstants.SINGLE_REPORT_TYPE, reportId
    )
    .add(
      configurationService.getOptimizeIndex(ElasticsearchConstants.COMBINED_REPORT_TYPE),
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


  public List<ReportDefinitionDto> getAllReports() {
    logger.debug("Fetching all available reports");
    SearchResponse scrollResp = esclient
      .prepareSearch(
        configurationService.getOptimizeIndex(ElasticsearchConstants.SINGLE_REPORT_TYPE),
        configurationService.getOptimizeIndex(ElasticsearchConstants.COMBINED_REPORT_TYPE)
      )
      .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
      .setQuery(QueryBuilders.matchAllQuery())
      .setSize(1000)
      .get();
    List<ReportDefinitionDto> reportRequests = new ArrayList<>();

    do {
      for (SearchHit hit : scrollResp.getHits().getHits()) {
        String responseAsString = hit.getSourceAsString();
        try {
          ReportDefinitionDto report =
            objectMapper.readValue(responseAsString, ReportDefinitionDto.class);
          reportRequests.add(report);
        } catch (IOException e) {
          String reason = "While retrieving all available reports "  +
            "it was not possible to deserialize a report from Elasticsearch! " +
            "Report response from Elasticsearch: " + responseAsString;
          logger.error(reason, e);
          throw new OptimizeRuntimeException(reason);
        }
      }

      scrollResp = esclient
        .prepareSearchScroll(scrollResp.getScrollId())
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .get();
    } while (scrollResp.getHits().getHits().length != 0);

    return reportRequests;
  }

}
