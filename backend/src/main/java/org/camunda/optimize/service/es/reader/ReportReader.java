package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionPersistenceDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
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

  public ReportDefinitionDto getReport(String reportId) throws IOException, OptimizeException {
    logger.debug("Fetching report with id [{}]", reportId);
    GetResponse getResponse = esclient
      .prepareGet(
        configurationService.getOptimizeIndex(),
        configurationService.getReportType(),
        reportId
      )
      .setRealtime(false)
      .get();

    if (getResponse.isExists()) {
      String responseAsString = getResponse.getSourceAsString();
      ReportDefinitionPersistenceDto persistenceDto =
        objectMapper.readValue(responseAsString, ReportDefinitionPersistenceDto.class);
      return convertToOriginalForm(persistenceDto);
    } else {
      logger.error("Was not able to retrieve report with id [{}] from Elasticsearch.", reportId);
      throw new OptimizeException("Report does not exist!");
    }
  }

  private ReportDefinitionDto convertToOriginalForm(ReportDefinitionPersistenceDto persistenceDto) throws IOException {
    ReportDefinitionDto original = new ReportDefinitionDto();
    original.setId(persistenceDto.getId());
    original.setName(persistenceDto.getName());
    original.setOwner(persistenceDto.getOwner());
    original.setCreated(persistenceDto.getCreated());
    original.setLastModified(persistenceDto.getLastModified());
    original.setLastModifier(persistenceDto.getLastModifier());
    ReportDataDto reportData =
      persistenceDto.getData() != null ? objectMapper.readValue(persistenceDto.getData(), ReportDataDto.class) : null;
    original.setData(reportData);
    return original;
  }

  public List<ReportDefinitionDto> getAllReports() throws IOException {
    logger.debug("Fetching all available reports");
    SearchResponse scrollResp = esclient
      .prepareSearch(configurationService.getOptimizeIndex())
      .setTypes(configurationService.getReportType())
      .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
      .setQuery(QueryBuilders.matchAllQuery())
      .setSize(100)
      .get();
    List<ReportDefinitionDto> reportRequests = new ArrayList<>();

    do {
      for (SearchHit hit : scrollResp.getHits().getHits()) {
        String responseAsString = hit.getSourceAsString();
        ReportDefinitionPersistenceDto persistenceDto =
          objectMapper.readValue(responseAsString, ReportDefinitionPersistenceDto.class);
        reportRequests.add(convertToOriginalForm(persistenceDto));
      }

      scrollResp = esclient
        .prepareSearchScroll(scrollResp.getScrollId())
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .get();
    } while (scrollResp.getHits().getHits().length != 0);

    return reportRequests;
  }

}
