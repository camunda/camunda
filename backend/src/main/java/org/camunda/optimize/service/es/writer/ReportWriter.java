package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.report.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.type.ReportType.CREATED;
import static org.camunda.optimize.service.es.schema.type.ReportType.ID;
import static org.camunda.optimize.service.es.schema.type.ReportType.LAST_MODIFIED;
import static org.camunda.optimize.service.es.schema.type.ReportType.LAST_MODIFIER;
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

  private SimpleDateFormat sdf;

  @PostConstruct
  private void init() {
    sdf = new SimpleDateFormat(configurationService.getDateFormat());
  }

  public IdDto createNewReportAndReturnId(String userId) {
    logger.debug("Writing new report to Elasticsearch");

    String id = IdGenerator.getNextId();
    Map<String, Object> json = new HashMap<>();
    json.put(CREATED, currentDateAsString());
    json.put(LAST_MODIFIED, currentDateAsString());
    json.put(OWNER, userId);
    json.put(LAST_MODIFIER, userId);
    json.put(ID, id);

    esclient
      .prepareIndex(
        configurationService.getOptimizeIndex(),
        configurationService.getReportType(),
        id
      )
      .setSource(json)
      .get();

    logger.debug("Report with id [{}] has successfully been created.", id);
    IdDto idDto = new IdDto();
    idDto.setId(id);
    return idDto;
  }

  public void updateReport(ReportDefinitionDto updatedReport) {
    logger.debug("Updating report with id [{}] in Elasticsearch");
    updatedReport.setLastModified(new Date());
    try {
      // updates only those fields that are defined in the updated report
      esclient
        .prepareUpdate(
          configurationService.getOptimizeIndex(),
          configurationService.getReportType(),
          updatedReport.getId())
        .setDoc(objectMapper.writeValueAsString(updatedReport), XContentType.JSON)
        .get();
    } catch (IOException e) {
      logger.error("Was not able to store report with id [{}] and name [{}]. Exception: {} \n Stacktrace: {}",
        updatedReport.getId(),
        updatedReport.getName(),
        e.getMessage(),
        e.getStackTrace());
    }
  }

  private String currentDateAsString() {
    return sdf.format(new Date());
  }

  public void deleteReport(String reportId) {
    esclient.prepareDelete(
      configurationService.getOptimizeIndex(),
      configurationService.getReportType(),
      reportId)
      .get();
  }

}
