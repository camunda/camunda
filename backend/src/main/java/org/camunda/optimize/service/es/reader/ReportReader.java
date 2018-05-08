package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.get.GetResponse;
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
   * @return fully serialized ReportDefinitionDto
   * @throws OptimizeRuntimeException if report with specified ID does not
   * exist or deserialization was not successful.
   */
  public ReportDefinitionDto getReport(String reportId) {
    logger.debug("Fetching report with id [{}]", reportId);
    GetResponse getResponse = esclient
      .prepareGet(
        configurationService.getOptimizeIndex(configurationService.getReportType()),
        configurationService.getReportType(),
        reportId
      )
      .setRealtime(false)
      .get();

    if (getResponse.isExists()) {
      String responseAsString = getResponse.getSourceAsString();
      try {
        ReportDefinitionDto report = objectMapper.readValue(responseAsString, ReportDefinitionDto.class);
        return report;
      } catch (IOException e) {
        String reason = "While retrieving report with id [" + reportId +
          "] could not deserialize report from Elasticsearch!";
        logger.error(reason, e);
        throw new OptimizeRuntimeException(reason);
      }
    } else {
      String reason = "Was not able to retrieve report with id [" + reportId +
        "] from Elasticsearch. Report does not exist.";
      logger.error(reason);
      throw new NotFoundException(reason);
    }
  }

  public List<ReportDefinitionDto> getAllReports() throws IOException {
    logger.debug("Fetching all available reports");
    SearchResponse scrollResp = esclient
      .prepareSearch(configurationService.getOptimizeIndex(configurationService.getReportType()))
      .setTypes(configurationService.getReportType())
      .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
      .setQuery(QueryBuilders.matchAllQuery())
      .setSize(100)
      .get();
    List<ReportDefinitionDto> reportRequests = new ArrayList<>();

    do {
      for (SearchHit hit : scrollResp.getHits().getHits()) {
        String responseAsString = hit.getSourceAsString();
        ReportDefinitionDto report =
          objectMapper.readValue(responseAsString, ReportDefinitionDto.class);
        reportRequests.add(report);
      }

      scrollResp = esclient
        .prepareSearchScroll(scrollResp.getScrollId())
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .get();
    } while (scrollResp.getHits().getHits().length != 0);

    return reportRequests;
  }

}
