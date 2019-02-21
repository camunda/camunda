package org.camunda.optimize.service.es.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.filter.DecisionQueryFilterEnhancer;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.report.command.Command;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.NotSupportedCommand;
import org.camunda.optimize.service.es.report.command.decision.RawDecisionDataCommand;
import org.camunda.optimize.service.es.report.command.decision.frequency.CountDecisionFrequencyGroupByEvaluationDateTimeCommand;
import org.camunda.optimize.service.es.report.command.decision.frequency.CountDecisionFrequencyGroupByInputVariableCommand;
import org.camunda.optimize.service.es.report.command.decision.frequency.CountDecisionFrequencyGroupByMatchedRuleCommand;
import org.camunda.optimize.service.es.report.command.decision.frequency.CountDecisionFrequencyGroupByNoneCommand;
import org.camunda.optimize.service.es.report.command.decision.frequency.CountDecisionFrequencyGroupByOutputVariableCommand;
import org.camunda.optimize.service.es.report.command.process.RawProcessDataCommand;
import org.camunda.optimize.service.es.report.command.process.flownode.duration.AverageFlowNodeDurationByFlowNodeCommand;
import org.camunda.optimize.service.es.report.command.process.flownode.duration.MaxFlowNodeDurationByFlowNodeCommand;
import org.camunda.optimize.service.es.report.command.process.flownode.duration.MedianFlowNodeDurationByFlowNodeCommand;
import org.camunda.optimize.service.es.report.command.process.flownode.duration.MinFlowNodeDurationByFlowNodeCommand;
import org.camunda.optimize.service.es.report.command.process.flownode.frequency.CountFlowNodeFrequencyByFlowNodeCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date.withoutprocesspart.AverageProcessInstanceDurationGroupByStartDateCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date.withoutprocesspart.MaxProcessInstanceDurationGroupByStartDateCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date.withoutprocesspart.MedianProcessInstanceDurationGroupByStartDateCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date.withoutprocesspart.MinProcessInstanceDurationGroupByStartDateCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date.withprocesspart.AverageProcessInstanceDurationGroupByStartDateWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date.withprocesspart.MaxProcessInstanceDurationGroupByStartDateWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date.withprocesspart.MedianProcessInstanceDurationGroupByStartDateWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date.withprocesspart.MinProcessInstanceDurationGroupByStartDateWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.none.withoutprocesspart.AverageProcessInstanceDurationGroupByNoneCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.none.withoutprocesspart.MaxProcessInstanceDurationGroupByNoneCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.none.withoutprocesspart.MedianProcessInstanceDurationGroupByNoneCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.none.withoutprocesspart.MinProcessInstanceDurationGroupByNoneCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.none.withprocesspart.AverageProcessInstanceDurationGroupByNoneWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.none.withprocesspart.MaxProcessInstanceDurationGroupByNoneWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.none.withprocesspart.MedianProcessInstanceDurationGroupByNoneWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.none.withprocesspart.MinProcessInstanceDurationGroupByNoneWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.variable.withoutprocesspart.AverageProcessInstanceDurationByVariableCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.variable.withoutprocesspart.MaxProcessInstanceDurationByVariableCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.variable.withoutprocesspart.MedianProcessInstanceDurationByVariableCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.variable.withoutprocesspart.MinProcessInstanceDurationByVariableCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.variable.withprocesspart.AverageProcessInstanceDurationGroupByVariableWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.variable.withprocesspart.MaxProcessInstanceDurationGroupByVariableWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.variable.withprocesspart.MedianProcessInstanceDurationGroupByVariableWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.variable.withprocesspart.MinProcessInstanceDurationGroupByVariableWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.frequency.CountProcessInstanceFrequencyByStartDateCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.frequency.CountProcessInstanceFrequencyByVariableCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.frequency.CountProcessInstanceFrequencyGroupByNoneCommand;
import org.camunda.optimize.service.es.report.command.process.user_task.duration.AverageUserTaskIdleDurationByUserTaskCommand;
import org.camunda.optimize.service.es.report.command.process.user_task.duration.AverageUserTaskTotalDurationByUserTaskCommand;
import org.camunda.optimize.service.es.report.command.process.user_task.duration.AverageUserTaskWorkDurationByUserTaskCommand;
import org.camunda.optimize.service.es.report.command.process.user_task.duration.MaxUserTaskIdleDurationByUserTaskCommand;
import org.camunda.optimize.service.es.report.command.process.user_task.duration.MaxUserTaskTotalDurationByUserTaskCommand;
import org.camunda.optimize.service.es.report.command.process.user_task.duration.MaxUserTaskWorkDurationByUserTaskCommand;
import org.camunda.optimize.service.es.report.command.process.user_task.duration.MedianUserTaskIdleDurationByUserTaskCommand;
import org.camunda.optimize.service.es.report.command.process.user_task.duration.MedianUserTaskTotalDurationByUserTaskCommand;
import org.camunda.optimize.service.es.report.command.process.user_task.duration.MedianUserTaskWorkDurationByUserTaskCommand;
import org.camunda.optimize.service.es.report.command.process.user_task.duration.MinUserTaskIdleDurationByUserTaskCommand;
import org.camunda.optimize.service.es.report.command.process.user_task.duration.MinUserTaskTotalDurationByUserTaskCommand;
import org.camunda.optimize.service.es.report.command.process.user_task.duration.MinUserTaskWorkDurationByUserTaskCommand;
import org.camunda.optimize.service.es.report.command.util.IntervalAggregationService;
import org.camunda.optimize.service.es.report.result.ReportResult;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.ValidationHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.decision.util.DecisionReportDataCreator.createCountFrequencyGroupByEvaluationDateTimeReport;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionReportDataCreator.createCountFrequencyGroupByInputVariableReport;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionReportDataCreator.createCountFrequencyGroupByMatchedRuleReport;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionReportDataCreator.createCountFrequencyGroupByNoneReport;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionReportDataCreator.createCountFrequencyGroupByOutputVariableReport;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionReportDataCreator.createRawDecisionDataReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createAverageFlowNodeDurationGroupByFlowNodeReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createAverageProcessInstanceDurationGroupByNoneReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createAverageProcessInstanceDurationGroupByNoneWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createAverageProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createAverageProcessInstanceDurationGroupByStartDateWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createAverageProcessInstanceDurationGroupByVariableReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createAverageProcessInstanceDurationGroupByVariableWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createAverageUserTaskIdleDurationGroupByUserTaskReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createAverageUserTaskTotalDurationGroupByUserTaskReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createAverageUserTaskWorkDurationGroupByUserTaskReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createCountFlowNodeFrequencyGroupByFlowNodeReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createCountProcessInstanceFrequencyGroupByNoneReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createCountProcessInstanceFrequencyGroupByStartDateReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createCountProcessInstanceFrequencyGroupByVariableReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMaxFlowNodeDurationGroupByFlowNodeReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMaxProcessInstanceDurationGroupByNoneReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMaxProcessInstanceDurationGroupByNoneWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMaxProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMaxProcessInstanceDurationGroupByStartDateWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMaxProcessInstanceDurationGroupByVariableReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMaxProcessInstanceDurationGroupByVariableWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMaxUserTaskIdleDurationGroupByUserTaskReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMaxUserTaskTotalDurationGroupByUserTaskReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMaxUserTaskWorkDurationGroupByUserTaskReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMedianFlowNodeDurationGroupByFlowNodeReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMedianProcessInstanceDurationGroupByNoneReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMedianProcessInstanceDurationGroupByNoneWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMedianProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMedianProcessInstanceDurationGroupByStartDateWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMedianProcessInstanceDurationGroupByVariableReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMedianProcessInstanceDurationGroupByVariableWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMedianUserTaskIdleDurationGroupByUserTaskReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMedianUserTaskTotalDurationGroupByUserTaskReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMedianUserTaskWorkDurationGroupByUserTaskReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMinFlowNodeDurationGroupByFlowNodeReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMinProcessInstanceDurationGroupByNoneReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMinProcessInstanceDurationGroupByNoneWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMinProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMinProcessInstanceDurationGroupByStartDateWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMinProcessInstanceDurationGroupByVariableReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMinProcessInstanceDurationGroupByVariableWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMinUserTaskIdleDurationGroupByUserTaskReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMinUserTaskTotalDurationGroupByUserTaskReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMinUserTaskWorkDurationGroupByUserTaskReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createRawDataReport;

@Component
public class SingleReportEvaluator {

  private static Map<String, Class<? extends Command>> possibleCommands = new HashMap<>();

  static {
    // process reports
    possibleCommands.put(createRawDataReport().createCommandKey(), RawProcessDataCommand.class);

    addCountProcessInstanceFrequencyReports();
    addCountFlowNodeFrequencyReports();
    addUserTaskIdleDurationReports();
    addUserTaskTotalDurationReports();
    addUserTaskWorkDurationReports();

    addAverageProcessInstanceDurationReports();
    addMinProcessInstanceDurationReports();
    addMaxProcessInstanceDurationReports();
    addMedianProcessInstanceDurationReports();

    addFlowNodeDurationReports();

    // decision reports
    possibleCommands.put(createRawDecisionDataReport().createCommandKey(), RawDecisionDataCommand.class);

    addDecisionCountFrequencyReports();
  }

  protected final ConfigurationService configurationService;
  protected final ObjectMapper objectMapper;
  protected final ProcessQueryFilterEnhancer processQueryFilterEnhancer;
  protected final DecisionQueryFilterEnhancer decisionQueryFilterEnhancer;
  protected final RestHighLevelClient esClient;
  protected final IntervalAggregationService intervalAggregationService;

  @Autowired
  public SingleReportEvaluator(ConfigurationService configurationService, ObjectMapper objectMapper,
                               ProcessQueryFilterEnhancer processQueryFilterEnhancer,
                               DecisionQueryFilterEnhancer decisionQueryFilterEnhancer, RestHighLevelClient esClient,
                               IntervalAggregationService intervalAggregationService) {
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
    this.processQueryFilterEnhancer = processQueryFilterEnhancer;
    this.decisionQueryFilterEnhancer = decisionQueryFilterEnhancer;
    this.esClient = esClient;
    this.intervalAggregationService = intervalAggregationService;
  }

  ReportResult evaluate(ReportDataDto reportData) throws OptimizeException {
    CommandContext<ReportDataDto> commandContext = createCommandContext(reportData);
    Command<ReportDataDto> evaluationCommand = extractCommandWithValidation(reportData);
    return evaluationCommand.evaluate(commandContext);
  }

  @SuppressWarnings(value = "unchecked")
  Command<ReportDataDto> extractCommand(ReportDataDto reportData) {
    try {
      return possibleCommands.getOrDefault(reportData.createCommandKey(), NotSupportedCommand.class).newInstance();
    } catch (InstantiationException | IllegalAccessException e) {
      throw new OptimizeRuntimeException("Failed creating command!", e);
    }
  }

  protected CommandContext<ReportDataDto> createCommandContext(ReportDataDto reportData) {
    CommandContext<ReportDataDto> commandContext = new CommandContext<>();
    commandContext.setConfigurationService(configurationService);
    commandContext.setEsClient(esClient);
    commandContext.setObjectMapper(objectMapper);
    commandContext.setIntervalAggregationService(intervalAggregationService);
    if (reportData instanceof ProcessReportDataDto) {
      commandContext.setQueryFilterEnhancer(processQueryFilterEnhancer);
    } else if (reportData instanceof DecisionReportDataDto) {
      commandContext.setQueryFilterEnhancer(decisionQueryFilterEnhancer);
    }
    commandContext.setReportData(reportData);
    return commandContext;
  }

  private Command<ReportDataDto> extractCommandWithValidation(ReportDataDto reportData) {
    ValidationHelper.validate(reportData);
    return extractCommand(reportData);
  }

  private static void addCountProcessInstanceFrequencyReports() {
    possibleCommands.put(
      createCountProcessInstanceFrequencyGroupByNoneReport().createCommandKey(),
      CountProcessInstanceFrequencyGroupByNoneCommand.class
    );
    possibleCommands.put(
      createCountProcessInstanceFrequencyGroupByStartDateReport().createCommandKey(),
      CountProcessInstanceFrequencyByStartDateCommand.class
    );
    possibleCommands.put(
      createCountProcessInstanceFrequencyGroupByVariableReport().createCommandKey(),
      CountProcessInstanceFrequencyByVariableCommand.class
    );
  }

  private static void addCountFlowNodeFrequencyReports() {
    possibleCommands.put(
      createCountFlowNodeFrequencyGroupByFlowNodeReport().createCommandKey(),
      CountFlowNodeFrequencyByFlowNodeCommand.class
    );
  }

  private static void addFlowNodeDurationReports() {
    possibleCommands.put(
      createAverageFlowNodeDurationGroupByFlowNodeReport().createCommandKey(),
      AverageFlowNodeDurationByFlowNodeCommand.class
    );
    possibleCommands.put(
      createMinFlowNodeDurationGroupByFlowNodeReport().createCommandKey(),
      MinFlowNodeDurationByFlowNodeCommand.class
    );
    possibleCommands.put(
      createMaxFlowNodeDurationGroupByFlowNodeReport().createCommandKey(),
      MaxFlowNodeDurationByFlowNodeCommand.class
    );
    possibleCommands.put(
      createMedianFlowNodeDurationGroupByFlowNodeReport().createCommandKey(),
      MedianFlowNodeDurationByFlowNodeCommand.class
    );
  }

  private static void addUserTaskIdleDurationReports() {
    possibleCommands.put(
      createAverageUserTaskIdleDurationGroupByUserTaskReport().createCommandKey(),
      AverageUserTaskIdleDurationByUserTaskCommand.class
    );
    possibleCommands.put(
      createMinUserTaskIdleDurationGroupByUserTaskReport().createCommandKey(),
      MinUserTaskIdleDurationByUserTaskCommand.class
    );
    possibleCommands.put(
      createMaxUserTaskIdleDurationGroupByUserTaskReport().createCommandKey(),
      MaxUserTaskIdleDurationByUserTaskCommand.class
    );
    possibleCommands.put(
      createMedianUserTaskIdleDurationGroupByUserTaskReport().createCommandKey(),
      MedianUserTaskIdleDurationByUserTaskCommand.class
    );
  }

  private static void addUserTaskTotalDurationReports() {
    possibleCommands.put(
      createAverageUserTaskTotalDurationGroupByUserTaskReport().createCommandKey(),
      AverageUserTaskTotalDurationByUserTaskCommand.class
    );
    possibleCommands.put(
      createMinUserTaskTotalDurationGroupByUserTaskReport().createCommandKey(),
      MinUserTaskTotalDurationByUserTaskCommand.class
    );
    possibleCommands.put(
      createMaxUserTaskTotalDurationGroupByUserTaskReport().createCommandKey(),
      MaxUserTaskTotalDurationByUserTaskCommand.class
    );
    possibleCommands.put(
      createMedianUserTaskTotalDurationGroupByUserTaskReport().createCommandKey(),
      MedianUserTaskTotalDurationByUserTaskCommand.class
    );
  }

  private static void addUserTaskWorkDurationReports() {
    possibleCommands.put(
      createAverageUserTaskWorkDurationGroupByUserTaskReport().createCommandKey(),
      AverageUserTaskWorkDurationByUserTaskCommand.class
    );
    possibleCommands.put(
      createMinUserTaskWorkDurationGroupByUserTaskReport().createCommandKey(),
      MinUserTaskWorkDurationByUserTaskCommand.class
    );
    possibleCommands.put(
      createMaxUserTaskWorkDurationGroupByUserTaskReport().createCommandKey(),
      MaxUserTaskWorkDurationByUserTaskCommand.class
    );
    possibleCommands.put(
      createMedianUserTaskWorkDurationGroupByUserTaskReport().createCommandKey(),
      MedianUserTaskWorkDurationByUserTaskCommand.class
    );
  }


  private static void addAverageProcessInstanceDurationReports() {
    possibleCommands.put(
      createAverageProcessInstanceDurationGroupByNoneReport().createCommandKey(),
      AverageProcessInstanceDurationGroupByNoneCommand.class
    );
    possibleCommands.put(
      createAverageProcessInstanceDurationGroupByNoneWithProcessPartReport().createCommandKey(),
      AverageProcessInstanceDurationGroupByNoneWithProcessPartCommand.class
    );
    possibleCommands.put(
      createAverageProcessInstanceDurationGroupByStartDateReport().createCommandKey(),
      AverageProcessInstanceDurationGroupByStartDateCommand.class
    );
    possibleCommands.put(
      createAverageProcessInstanceDurationGroupByStartDateWithProcessPartReport().createCommandKey(),
      AverageProcessInstanceDurationGroupByStartDateWithProcessPartCommand.class
    );
    possibleCommands.put(
      createAverageProcessInstanceDurationGroupByVariableReport().createCommandKey(),
      AverageProcessInstanceDurationByVariableCommand.class
    );
    possibleCommands.put(
      createAverageProcessInstanceDurationGroupByVariableWithProcessPartReport().createCommandKey(),
      AverageProcessInstanceDurationGroupByVariableWithProcessPartCommand.class
    );
  }

  private static void addMinProcessInstanceDurationReports() {
    possibleCommands.put(
      createMinProcessInstanceDurationGroupByNoneReport().createCommandKey(),
      MinProcessInstanceDurationGroupByNoneCommand.class
    );
    possibleCommands.put(
      createMinProcessInstanceDurationGroupByNoneWithProcessPartReport().createCommandKey(),
      MinProcessInstanceDurationGroupByNoneWithProcessPartCommand.class
    );
    possibleCommands.put(
      createMinProcessInstanceDurationGroupByStartDateReport().createCommandKey(),
      MinProcessInstanceDurationGroupByStartDateCommand.class
    );
    possibleCommands.put(
      createMinProcessInstanceDurationGroupByStartDateWithProcessPartReport().createCommandKey(),
      MinProcessInstanceDurationGroupByStartDateWithProcessPartCommand.class
    );
    possibleCommands.put(
      createMinProcessInstanceDurationGroupByVariableReport().createCommandKey(),
      MinProcessInstanceDurationByVariableCommand.class
    );
    possibleCommands.put(
      createMinProcessInstanceDurationGroupByVariableWithProcessPartReport().createCommandKey(),
      MinProcessInstanceDurationGroupByVariableWithProcessPartCommand.class
    );
  }

  private static void addMaxProcessInstanceDurationReports() {
    possibleCommands.put(
      createMaxProcessInstanceDurationGroupByNoneReport().createCommandKey(),
      MaxProcessInstanceDurationGroupByNoneCommand.class
    );
    possibleCommands.put(
      createMaxProcessInstanceDurationGroupByNoneWithProcessPartReport().createCommandKey(),
      MaxProcessInstanceDurationGroupByNoneWithProcessPartCommand.class
    );
    possibleCommands.put(
      createMaxProcessInstanceDurationGroupByStartDateReport().createCommandKey(),
      MaxProcessInstanceDurationGroupByStartDateCommand.class
    );
    possibleCommands.put(
      createMaxProcessInstanceDurationGroupByStartDateWithProcessPartReport().createCommandKey(),
      MaxProcessInstanceDurationGroupByStartDateWithProcessPartCommand.class
    );
    possibleCommands.put(
      createMaxProcessInstanceDurationGroupByVariableReport().createCommandKey(),
      MaxProcessInstanceDurationByVariableCommand.class
    );
    possibleCommands.put(
      createMaxProcessInstanceDurationGroupByVariableWithProcessPartReport().createCommandKey(),
      MaxProcessInstanceDurationGroupByVariableWithProcessPartCommand.class
    );
  }

  private static void addMedianProcessInstanceDurationReports() {
    possibleCommands.put(
      createMedianProcessInstanceDurationGroupByNoneReport().createCommandKey(),
      MedianProcessInstanceDurationGroupByNoneCommand.class
    );
    possibleCommands.put(
      createMedianProcessInstanceDurationGroupByNoneWithProcessPartReport().createCommandKey(),
      MedianProcessInstanceDurationGroupByNoneWithProcessPartCommand.class
    );
    possibleCommands.put(
      createMedianProcessInstanceDurationGroupByStartDateReport().createCommandKey(),
      MedianProcessInstanceDurationGroupByStartDateCommand.class
    );
    possibleCommands.put(
      createMedianProcessInstanceDurationGroupByStartDateWithProcessPartReport().createCommandKey(),
      MedianProcessInstanceDurationGroupByStartDateWithProcessPartCommand.class
    );
    possibleCommands.put(
      createMedianProcessInstanceDurationGroupByVariableReport().createCommandKey(),
      MedianProcessInstanceDurationByVariableCommand.class
    );
    possibleCommands.put(
      createMedianProcessInstanceDurationGroupByVariableWithProcessPartReport().createCommandKey(),
      MedianProcessInstanceDurationGroupByVariableWithProcessPartCommand.class
    );
  }

  private static void addDecisionCountFrequencyReports() {
    possibleCommands.put(
      createCountFrequencyGroupByNoneReport().createCommandKey(),
      CountDecisionFrequencyGroupByNoneCommand.class
    );
    possibleCommands.put(
      createCountFrequencyGroupByEvaluationDateTimeReport().createCommandKey(),
      CountDecisionFrequencyGroupByEvaluationDateTimeCommand.class
    );
    possibleCommands.put(
      createCountFrequencyGroupByInputVariableReport().createCommandKey(),
      CountDecisionFrequencyGroupByInputVariableCommand.class
    );
    possibleCommands.put(
      createCountFrequencyGroupByOutputVariableReport().createCommandKey(),
      CountDecisionFrequencyGroupByOutputVariableCommand.class
    );
    possibleCommands.put(
      createCountFrequencyGroupByMatchedRuleReport().createCommandKey(),
      CountDecisionFrequencyGroupByMatchedRuleCommand.class
    );
  }
}
