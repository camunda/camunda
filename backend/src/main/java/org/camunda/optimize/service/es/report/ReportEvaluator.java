package org.camunda.optimize.service.es.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ViewDto;
import org.camunda.optimize.dto.optimize.query.report.group.GroupByDto;
import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;
import org.camunda.optimize.service.es.filter.QueryFilterEnhancer;
import org.camunda.optimize.service.es.report.command.Command;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.NotSupportedCommand;
import org.camunda.optimize.service.es.report.command.RawDataCommand;
import org.camunda.optimize.service.es.report.command.flownode.duration.AverageFlowNodeDurationByFlowNodeCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.date.MaxProcessInstanceDurationGroupedByStartDateCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.date.MedianProcessInstanceDurationGroupedByStartDateCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.date.MinProcessInstanceDurationGroupedByStartDateCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.none.MaxProcessInstanceDurationGroupByNoneCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.none.MedianProcessInstanceDurationGroupByNoneCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.none.MinProcessInstanceDurationGroupByNoneCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.variable.AverageProcessInstanceDurationByVariableCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.none.AverageProcessInstanceDurationGroupByNoneCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.date.AverageProcessInstanceDurationGroupedByStartDateCommand;
import org.camunda.optimize.service.es.report.command.flownode.frequency.CountFlowNodeFrequencyByFlowNodeCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.variable.MaxProcessInstanceDurationByVariableCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.variable.MedianProcessInstanceDurationByVariableCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.variable.MinProcessInstanceDurationByVariableCommand;
import org.camunda.optimize.service.es.report.command.pi.frequency.CountProcessInstanceFrequencyByStartDateCommand;
import org.camunda.optimize.service.es.report.command.pi.frequency.CountProcessInstanceFrequencyByVariableCommand;
import org.camunda.optimize.service.es.report.command.pi.frequency.CountProcessInstanceFrequencyGroupByNoneCommand;
import org.camunda.optimize.service.es.report.command.flownode.duration.MaxFlowNodeDurationByFlowNodeCommand;
import org.camunda.optimize.service.es.report.command.flownode.duration.MedianFlowNodeDurationByFlowNodeCommand;
import org.camunda.optimize.service.es.report.command.flownode.duration.MinFlowNodeDurationByFlowNodeCommand;
import org.camunda.optimize.service.es.report.command.util.ReportUtil;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.ValidationHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.Client;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.util.GroupByDtoCreator.createGroupByFlowNode;
import static org.camunda.optimize.service.es.report.command.util.GroupByDtoCreator.createGroupByNone;
import static org.camunda.optimize.service.es.report.command.util.GroupByDtoCreator.createGroupByStartDateDto;
import static org.camunda.optimize.service.es.report.command.util.GroupByDtoCreator.createGroupByVariable;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createAverageFlowNodeDurationView;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createAverageProcessInstanceDurationView;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createCountFlowNodeFrequencyView;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createCountProcessInstanceFrequencyView;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createMaxFlowNodeDurationView;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createMaxProcessInstanceDurationView;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createMedianFlowNodeDurationView;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createMedianProcessInstanceDurationView;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createMinFlowNodeDurationView;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createMinProcessInstanceDurationView;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createRawDataView;

@Component
public class ReportEvaluator {

  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private QueryFilterEnhancer queryFilterEnhancer;
  @Autowired
  private Client esclient;

  private static Map<String, Map<String, Command>> viewToGroupByToCommand = new HashMap<>();

  static {
    addRawDataView();

    addCountProcessInstanceFrequencyView();
    addCountFlowNodeFrequencyView();

    addAverageProcessInstanceDurationView();
    addMinProcessInstanceDurationView();
    addMaxProcessInstanceDurationView();
    addMedianProcessInstanceDurationView();

    addAverageFlowNodeDurationView();
    addMindFlowNodeDurationView();
    addMaxFlowNodeDurationView();
    addMedianFlowNodeDurationView();
  }

  public ReportResultDto evaluate(ReportDataDto reportData) throws OptimizeException {
    CommandContext commandContext = createCommandContext(reportData);
    Command evaluationCommand = extractCommand(reportData);
    ReportResultDto result = evaluationCommand.evaluate(commandContext);
    ReportUtil.copyReportData(reportData, result);
    return result;
  }

  private Command extractCommand(ReportDataDto reportData) {
    ValidationHelper.validate(reportData);
    ViewDto view = reportData.getView();
    GroupByDto groupBy = reportData.getGroupBy();

    Command command;
    if(viewToGroupByToCommand.containsKey(view.getKey())) {
      Map<String, Command> groupByToCommand = viewToGroupByToCommand.get(view.getKey());
      command = groupByToCommand.getOrDefault(groupBy.getKey(), new NotSupportedCommand());
    } else {
      command = new NotSupportedCommand();
    }
    return command;
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

  private static void addMedianFlowNodeDurationView() {
    Map<String, Command> groupByToCommand;
    groupByToCommand = new HashMap<>();
    groupByToCommand.put(createGroupByFlowNode().getKey(), new MedianFlowNodeDurationByFlowNodeCommand());
    viewToGroupByToCommand.put(createMedianFlowNodeDurationView().getKey(), groupByToCommand);
  }

  private static void addMaxFlowNodeDurationView() {
    Map<String, Command> groupByToCommand;
    groupByToCommand = new HashMap<>();
    groupByToCommand.put(createGroupByFlowNode().getKey(), new MaxFlowNodeDurationByFlowNodeCommand());
    viewToGroupByToCommand.put(createMaxFlowNodeDurationView().getKey(), groupByToCommand);
  }

  private static void addMindFlowNodeDurationView() {
    Map<String, Command> groupByToCommand;
    groupByToCommand = new HashMap<>();
    groupByToCommand.put(createGroupByFlowNode().getKey(), new MinFlowNodeDurationByFlowNodeCommand());
    viewToGroupByToCommand.put(createMinFlowNodeDurationView().getKey(), groupByToCommand);
  }

  private static void addAverageFlowNodeDurationView() {
    Map<String, Command> groupByToCommand;
    groupByToCommand = new HashMap<>();
    groupByToCommand.put(createGroupByFlowNode().getKey(), new AverageFlowNodeDurationByFlowNodeCommand());
    viewToGroupByToCommand.put(createAverageFlowNodeDurationView().getKey(), groupByToCommand);
  }

  private static void addRawDataView() {
    Map<String, Command> groupByToCommand = new HashMap<>();
    groupByToCommand.put(createGroupByNone().getKey(), new RawDataCommand());
    viewToGroupByToCommand.put(createRawDataView().getKey(), groupByToCommand);
  }

  private static void addCountProcessInstanceFrequencyView() {
    Map<String, Command> groupByToCommand;
    groupByToCommand = new HashMap<>();
    groupByToCommand.put(createGroupByNone().getKey(), new CountProcessInstanceFrequencyGroupByNoneCommand());
    groupByToCommand.put(createGroupByStartDateDto().getKey(), new CountProcessInstanceFrequencyByStartDateCommand());
    groupByToCommand.put(createGroupByVariable().getKey(), new CountProcessInstanceFrequencyByVariableCommand());
    viewToGroupByToCommand.put(createCountProcessInstanceFrequencyView().getKey(), groupByToCommand);
  }

  private static void addCountFlowNodeFrequencyView() {
    Map<String, Command> groupByToCommand;
    groupByToCommand = new HashMap<>();
    groupByToCommand.put(createGroupByFlowNode().getKey(), new CountFlowNodeFrequencyByFlowNodeCommand());
    viewToGroupByToCommand.put(createCountFlowNodeFrequencyView().getKey(), groupByToCommand);
  }

  private static void addAverageProcessInstanceDurationView() {
    Map<String, Command> groupByToCommand;
    groupByToCommand = new HashMap<>();
    groupByToCommand.put(
      createGroupByNone().getKey(),
      new AverageProcessInstanceDurationGroupByNoneCommand()
    );
    groupByToCommand.put(
      createGroupByStartDateDto().getKey(),
      new AverageProcessInstanceDurationGroupedByStartDateCommand()
    );
    groupByToCommand.put(createGroupByVariable().getKey(), new AverageProcessInstanceDurationByVariableCommand());
    viewToGroupByToCommand.put(createAverageProcessInstanceDurationView().getKey(), groupByToCommand);
  }

  private static void addMinProcessInstanceDurationView() {
    Map<String, Command> groupByToCommand;
    groupByToCommand = new HashMap<>();
    groupByToCommand.put(
      createGroupByNone().getKey(),
      new MinProcessInstanceDurationGroupByNoneCommand()
    );
    groupByToCommand.put(
      createGroupByStartDateDto().getKey(),
      new MinProcessInstanceDurationGroupedByStartDateCommand()
    );
    groupByToCommand.put(createGroupByVariable().getKey(), new MinProcessInstanceDurationByVariableCommand());
    viewToGroupByToCommand.put(createMinProcessInstanceDurationView().getKey(), groupByToCommand);
  }

  private static void addMaxProcessInstanceDurationView() {
    Map<String, Command> groupByToCommand;
    groupByToCommand = new HashMap<>();
    groupByToCommand.put(
      createGroupByNone().getKey(),
      new MaxProcessInstanceDurationGroupByNoneCommand()
    );
    groupByToCommand.put(
      createGroupByStartDateDto().getKey(),
      new MaxProcessInstanceDurationGroupedByStartDateCommand()
    );
    groupByToCommand.put(createGroupByVariable().getKey(), new MaxProcessInstanceDurationByVariableCommand());
    viewToGroupByToCommand.put(createMaxProcessInstanceDurationView().getKey(), groupByToCommand);
  }

  private static void addMedianProcessInstanceDurationView() {
    Map<String, Command> groupByToCommand;
    groupByToCommand = new HashMap<>();
    groupByToCommand.put(
      createGroupByNone().getKey(),
      new MedianProcessInstanceDurationGroupByNoneCommand()
    );
    groupByToCommand.put(
      createGroupByStartDateDto().getKey(),
      new MedianProcessInstanceDurationGroupedByStartDateCommand()
    );
    groupByToCommand.put(createGroupByVariable().getKey(), new MedianProcessInstanceDurationByVariableCommand());
    viewToGroupByToCommand.put(createMedianProcessInstanceDurationView().getKey(), groupByToCommand);
  }

}
