package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionPersistenceDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.type.ReportType.CREATED;
import static org.camunda.optimize.service.es.schema.type.ReportType.ID;
import static org.camunda.optimize.service.es.schema.type.ReportType.LAST_MODIFIED;
import static org.camunda.optimize.service.es.schema.type.ReportType.LAST_MODIFIER;
import static org.camunda.optimize.service.es.schema.type.ReportType.NAME;
import static org.camunda.optimize.service.es.schema.type.ReportType.OWNER;

@Component
public class ReportWriter {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private Client esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private DateTimeFormatter dateTimeFormatter;

  private static final String DEFAULT_REPORT_NAME = "New Report";

  public IdDto createNewReportAndReturnId(String userId) {
    logger.debug("Writing new report to Elasticsearch");

    String id = IdGenerator.getNextId();
    Map<String, Object> map = new HashMap<>();
    map.put(CREATED, currentDateAsString());
    map.put(LAST_MODIFIED, currentDateAsString());
    map.put(OWNER, userId);
    map.put(LAST_MODIFIER, userId);
    map.put(NAME, DEFAULT_REPORT_NAME);
    map.put(ID, id);

    esclient
      .prepareIndex(
        configurationService.getOptimizeIndex(configurationService.getReportType()),
        configurationService.getReportType(),
        id
      )
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .setSource(map)
      .get();

    logger.debug("Report with id [{}] has successfully been created.", id);
    IdDto idDto = new IdDto();
    idDto.setId(id);
    return idDto;
  }

  public void updateReport(ReportDefinitionDto updatedReport) throws OptimizeException, JsonProcessingException {
    logger.debug("Updating report with id [{}] in Elasticsearch", updatedReport.getId());
    ReportDefinitionPersistenceDto persistenceDto = convertToElasticsearchFormat(updatedReport);
    UpdateResponse updateResponse = esclient
      .prepareUpdate(
        configurationService.getOptimizeIndex(configurationService.getReportType()),
        configurationService.getReportType(),
        updatedReport.getId()
      )
      .setDoc(objectMapper.writeValueAsString(persistenceDto), XContentType.JSON)
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .get();

    if (updateResponse.getShardInfo().getFailed() > 0) {
      logger.error("Was not able to store report with id [{}] and name [{}]. Exception: {} \n Stacktrace: {}",
        updatedReport.getId(),
        updatedReport.getName());
      throw new OptimizeException("Was not able to store report!");
    }
  }

  private ReportDefinitionPersistenceDto convertToElasticsearchFormat(ReportDefinitionDto original) throws JsonProcessingException {
    ReportDefinitionPersistenceDto persistenceDto = new ReportDefinitionPersistenceDto();
    persistenceDto.setCreated(original.getCreated());
    persistenceDto.setData(objectMapper.writeValueAsString(original.getData()));
    persistenceDto.setId(original.getId());
    persistenceDto.setLastModified(original.getLastModified());
    persistenceDto.setLastModifier(original.getLastModifier());
    persistenceDto.setName(original.getName());
    persistenceDto.setOwner(original.getOwner());
    return persistenceDto;
  }

  private String currentDateAsString() {
    return dateTimeFormatter.format(LocalDateUtil.getCurrentDateTime());
  }

  public void deleteReport(String reportId) {
    logger.debug("Deleting report with id [{}]");
    esclient.prepareDelete(
      configurationService.getOptimizeIndex(configurationService.getReportType()),
      configurationService.getReportType(),
      reportId
    )
    .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
    .get();
  }

}
