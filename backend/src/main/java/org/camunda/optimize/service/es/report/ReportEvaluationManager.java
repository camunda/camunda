package org.camunda.optimize.service.es.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.report.GroupByDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ViewDto;
import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;
import org.camunda.optimize.service.es.filter.QueryFilterEnhancer;
import org.camunda.optimize.service.es.report.command.Command;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.count.CountFlowNodeFrequencyByFlowNodeCommand;
import org.camunda.optimize.service.es.report.command.count.CountProcessInstanceFrequencyByStartDateCommand;
import org.camunda.optimize.service.es.report.command.count.CountTotalProcessInstanceFrequencyCommand;
import org.camunda.optimize.service.es.report.command.NotSupportedCommand;
import org.camunda.optimize.service.es.report.command.RawDataCommand;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.ValidationHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.Client;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.GROUP_BY_FLOW_NODE_TYPE;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.GROUP_BY_NONE_TYPE;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.GROUP_BY_START_DATE_TYPE;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_COUNT_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_FLOW_NODE_ENTITY;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_FREQUENCY_PROPERTY;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_PROCESS_INSTANCE_ENTITY;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_RAW_DATA_OPERATION;

@Component
public class ReportEvaluationManager {

  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private QueryFilterEnhancer queryFilterEnhancer;
  @Autowired
  private Client esclient;

  public ReportResultDto evaluate(ReportDataDto reportData) throws IOException, OptimizeException {
    CommandContext commandContext = createCommandContext(reportData);
    Command evaluationCommand = extractCommand(reportData);
    ReportResultDto result = evaluationCommand.evaluate(commandContext);
    result.copyReportDataProperties(reportData);
    return result;
  }

  private Command extractCommand(ReportDataDto reportData) {
    ValidationHelper.validate(reportData);

    ViewDto view = reportData.getView();
    String operation = view.getOperation();
    Command evaluationCommand = new NotSupportedCommand();
    switch (operation) {
      case VIEW_RAW_DATA_OPERATION:
        evaluationCommand = new RawDataCommand();
        break;
      case VIEW_COUNT_OPERATION:
        evaluationCommand = extractEntityForCountOperation(reportData);
        break;
    }
    return evaluationCommand;
  }

  private Command extractEntityForCountOperation(ReportDataDto reportData) {
    Command evaluationCommand = new NotSupportedCommand();
    String entity = reportData.getView().getEntity();
    ValidationHelper.ensureNotEmpty("view entity", entity);
    switch (entity) {
      case VIEW_FLOW_NODE_ENTITY:
        evaluationCommand = extractPropertyForCountFlowNode(reportData);
        break;
      case VIEW_PROCESS_INSTANCE_ENTITY:
        evaluationCommand = extractPropertyForCountProcessInstance(reportData);
        break;
    }
    return evaluationCommand;
  }

  private Command extractPropertyForCountProcessInstance(ReportDataDto reportData) {
    Command evaluationCommand = new NotSupportedCommand();
    String property = reportData.getView().getProperty();
    ValidationHelper.ensureNotEmpty("view property", property);
    switch (property) {
      case VIEW_FREQUENCY_PROPERTY:
        evaluationCommand = extractGroupForCountProcessInstanceFrequency(reportData);
        break;
    }
    return evaluationCommand;
  }

  private Command extractGroupForCountProcessInstanceFrequency(ReportDataDto reportData) {
    Command evaluationCommand = new NotSupportedCommand();
    GroupByDto groupBy = reportData.getGroupBy();
    ValidationHelper.ensureNotNull("group by", groupBy);
    String type = groupBy.getType();
    ValidationHelper.ensureNotEmpty("group by type", type);
    switch (type) {
      case GROUP_BY_NONE_TYPE:
        evaluationCommand = new CountTotalProcessInstanceFrequencyCommand();
        break;
      case GROUP_BY_START_DATE_TYPE:
        ValidationHelper.ensureNotEmpty("group by unit", groupBy.getUnit());
        evaluationCommand = new CountProcessInstanceFrequencyByStartDateCommand();
        break;
    }
    return evaluationCommand;
  }

  private Command extractPropertyForCountFlowNode(ReportDataDto reportData) {
    Command evaluationCommand = new NotSupportedCommand();
    String property = reportData.getView().getProperty();
    ValidationHelper.ensureNotEmpty("view property", property);
    switch (property) {
      case VIEW_FREQUENCY_PROPERTY:
        evaluationCommand = extractGroupForCountFlowNodeFrequency(reportData);
        break;
    }
    return evaluationCommand;
  }

  private Command extractGroupForCountFlowNodeFrequency(ReportDataDto reportData) {
    Command evaluationCommand = new NotSupportedCommand();
    ValidationHelper.ensureNotNull("group by", reportData.getGroupBy());
    String type = reportData.getGroupBy().getType();
    ValidationHelper.ensureNotEmpty("group by type", type);
    switch (type) {
      case GROUP_BY_FLOW_NODE_TYPE:
        evaluationCommand = new CountFlowNodeFrequencyByFlowNodeCommand();
        break;
    }
    return evaluationCommand;
  }

  private CommandContext createCommandContext(ReportDataDto reportData) {
    CommandContext commandContext = new CommandContext();
    commandContext.setConfigurationService(configurationService);
    commandContext.setEsclient(esclient);
    commandContext.setObjectMapper(objectMapper);
    commandContext.setQueryFilterEnhancer(queryFilterEnhancer);
    commandContext.setReportData(reportData);
    return commandContext;
  }


}
