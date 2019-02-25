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
import org.camunda.optimize.service.util.ValidationHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

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

  private static Map<String, Supplier<? extends Command>> commandSuppliers = new HashMap<>();

  static {
    // process reports
    commandSuppliers.put(createRawDataReport().createCommandKey(), RawProcessDataCommand::new);

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
    commandSuppliers.put(createRawDecisionDataReport().createCommandKey(), RawDecisionDataCommand::new);

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
    return commandSuppliers.getOrDefault(reportData.createCommandKey(), NotSupportedCommand::new).get();
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
    commandSuppliers.put(
      createCountProcessInstanceFrequencyGroupByNoneReport().createCommandKey(),
      CountProcessInstanceFrequencyGroupByNoneCommand::new
    );
    commandSuppliers.put(
      createCountProcessInstanceFrequencyGroupByStartDateReport().createCommandKey(),
      CountProcessInstanceFrequencyByStartDateCommand::new
    );
    commandSuppliers.put(
      createCountProcessInstanceFrequencyGroupByVariableReport().createCommandKey(),
      CountProcessInstanceFrequencyByVariableCommand::new
    );
  }

  private static void addCountFlowNodeFrequencyReports() {
    commandSuppliers.put(
      createCountFlowNodeFrequencyGroupByFlowNodeReport().createCommandKey(),
      CountFlowNodeFrequencyByFlowNodeCommand::new
    );
  }

  private static void addFlowNodeDurationReports() {
    commandSuppliers.put(
      createAverageFlowNodeDurationGroupByFlowNodeReport().createCommandKey(),
      AverageFlowNodeDurationByFlowNodeCommand::new
    );
    commandSuppliers.put(
      createMinFlowNodeDurationGroupByFlowNodeReport().createCommandKey(),
      MinFlowNodeDurationByFlowNodeCommand::new
    );
    commandSuppliers.put(
      createMaxFlowNodeDurationGroupByFlowNodeReport().createCommandKey(),
      MaxFlowNodeDurationByFlowNodeCommand::new
    );
    commandSuppliers.put(
      createMedianFlowNodeDurationGroupByFlowNodeReport().createCommandKey(),
      MedianFlowNodeDurationByFlowNodeCommand::new
    );
  }

  private static void addUserTaskIdleDurationReports() {
    commandSuppliers.put(
      createAverageUserTaskIdleDurationGroupByUserTaskReport().createCommandKey(),
      AverageUserTaskIdleDurationByUserTaskCommand::new
    );
    commandSuppliers.put(
      createMinUserTaskIdleDurationGroupByUserTaskReport().createCommandKey(),
      MinUserTaskIdleDurationByUserTaskCommand::new
    );
    commandSuppliers.put(
      createMaxUserTaskIdleDurationGroupByUserTaskReport().createCommandKey(),
      MaxUserTaskIdleDurationByUserTaskCommand::new
    );
    commandSuppliers.put(
      createMedianUserTaskIdleDurationGroupByUserTaskReport().createCommandKey(),
      MedianUserTaskIdleDurationByUserTaskCommand::new
    );
  }

  private static void addUserTaskTotalDurationReports() {
    commandSuppliers.put(
      createAverageUserTaskTotalDurationGroupByUserTaskReport().createCommandKey(),
      AverageUserTaskTotalDurationByUserTaskCommand::new
    );
    commandSuppliers.put(
      createMinUserTaskTotalDurationGroupByUserTaskReport().createCommandKey(),
      MinUserTaskTotalDurationByUserTaskCommand::new
    );
    commandSuppliers.put(
      createMaxUserTaskTotalDurationGroupByUserTaskReport().createCommandKey(),
      MaxUserTaskTotalDurationByUserTaskCommand::new
    );
    commandSuppliers.put(
      createMedianUserTaskTotalDurationGroupByUserTaskReport().createCommandKey(),
      MedianUserTaskTotalDurationByUserTaskCommand::new
    );
  }

  private static void addUserTaskWorkDurationReports() {
    commandSuppliers.put(
      createAverageUserTaskWorkDurationGroupByUserTaskReport().createCommandKey(),
      AverageUserTaskWorkDurationByUserTaskCommand::new
    );
    commandSuppliers.put(
      createMinUserTaskWorkDurationGroupByUserTaskReport().createCommandKey(),
      MinUserTaskWorkDurationByUserTaskCommand::new
    );
    commandSuppliers.put(
      createMaxUserTaskWorkDurationGroupByUserTaskReport().createCommandKey(),
      MaxUserTaskWorkDurationByUserTaskCommand::new
    );
    commandSuppliers.put(
      createMedianUserTaskWorkDurationGroupByUserTaskReport().createCommandKey(),
      MedianUserTaskWorkDurationByUserTaskCommand::new
    );
  }


  private static void addAverageProcessInstanceDurationReports() {
    commandSuppliers.put(
      createAverageProcessInstanceDurationGroupByNoneReport().createCommandKey(),
      AverageProcessInstanceDurationGroupByNoneCommand::new
    );
    commandSuppliers.put(
      createAverageProcessInstanceDurationGroupByNoneWithProcessPartReport().createCommandKey(),
      AverageProcessInstanceDurationGroupByNoneWithProcessPartCommand::new
    );
    commandSuppliers.put(
      createAverageProcessInstanceDurationGroupByStartDateReport().createCommandKey(),
      AverageProcessInstanceDurationGroupByStartDateCommand::new
    );
    commandSuppliers.put(
      createAverageProcessInstanceDurationGroupByStartDateWithProcessPartReport().createCommandKey(),
      AverageProcessInstanceDurationGroupByStartDateWithProcessPartCommand::new
    );
    commandSuppliers.put(
      createAverageProcessInstanceDurationGroupByVariableReport().createCommandKey(),
      AverageProcessInstanceDurationByVariableCommand::new
    );
    commandSuppliers.put(
      createAverageProcessInstanceDurationGroupByVariableWithProcessPartReport().createCommandKey(),
      AverageProcessInstanceDurationGroupByVariableWithProcessPartCommand::new
    );
  }

  private static void addMinProcessInstanceDurationReports() {
    commandSuppliers.put(
      createMinProcessInstanceDurationGroupByNoneReport().createCommandKey(),
      MinProcessInstanceDurationGroupByNoneCommand::new
    );
    commandSuppliers.put(
      createMinProcessInstanceDurationGroupByNoneWithProcessPartReport().createCommandKey(),
      MinProcessInstanceDurationGroupByNoneWithProcessPartCommand::new
    );
    commandSuppliers.put(
      createMinProcessInstanceDurationGroupByStartDateReport().createCommandKey(),
      MinProcessInstanceDurationGroupByStartDateCommand::new
    );
    commandSuppliers.put(
      createMinProcessInstanceDurationGroupByStartDateWithProcessPartReport().createCommandKey(),
      MinProcessInstanceDurationGroupByStartDateWithProcessPartCommand::new
    );
    commandSuppliers.put(
      createMinProcessInstanceDurationGroupByVariableReport().createCommandKey(),
      MinProcessInstanceDurationByVariableCommand::new
    );
    commandSuppliers.put(
      createMinProcessInstanceDurationGroupByVariableWithProcessPartReport().createCommandKey(),
      MinProcessInstanceDurationGroupByVariableWithProcessPartCommand::new
    );
  }

  private static void addMaxProcessInstanceDurationReports() {
    commandSuppliers.put(
      createMaxProcessInstanceDurationGroupByNoneReport().createCommandKey(),
      MaxProcessInstanceDurationGroupByNoneCommand::new
    );
    commandSuppliers.put(
      createMaxProcessInstanceDurationGroupByNoneWithProcessPartReport().createCommandKey(),
      MaxProcessInstanceDurationGroupByNoneWithProcessPartCommand::new
    );
    commandSuppliers.put(
      createMaxProcessInstanceDurationGroupByStartDateReport().createCommandKey(),
      MaxProcessInstanceDurationGroupByStartDateCommand::new
    );
    commandSuppliers.put(
      createMaxProcessInstanceDurationGroupByStartDateWithProcessPartReport().createCommandKey(),
      MaxProcessInstanceDurationGroupByStartDateWithProcessPartCommand::new
    );
    commandSuppliers.put(
      createMaxProcessInstanceDurationGroupByVariableReport().createCommandKey(),
      MaxProcessInstanceDurationByVariableCommand::new
    );
    commandSuppliers.put(
      createMaxProcessInstanceDurationGroupByVariableWithProcessPartReport().createCommandKey(),
      MaxProcessInstanceDurationGroupByVariableWithProcessPartCommand::new
    );
  }

  private static void addMedianProcessInstanceDurationReports() {
    commandSuppliers.put(
      createMedianProcessInstanceDurationGroupByNoneReport().createCommandKey(),
      MedianProcessInstanceDurationGroupByNoneCommand::new
    );
    commandSuppliers.put(
      createMedianProcessInstanceDurationGroupByNoneWithProcessPartReport().createCommandKey(),
      MedianProcessInstanceDurationGroupByNoneWithProcessPartCommand::new
    );
    commandSuppliers.put(
      createMedianProcessInstanceDurationGroupByStartDateReport().createCommandKey(),
      MedianProcessInstanceDurationGroupByStartDateCommand::new
    );
    commandSuppliers.put(
      createMedianProcessInstanceDurationGroupByStartDateWithProcessPartReport().createCommandKey(),
      MedianProcessInstanceDurationGroupByStartDateWithProcessPartCommand::new
    );
    commandSuppliers.put(
      createMedianProcessInstanceDurationGroupByVariableReport().createCommandKey(),
      MedianProcessInstanceDurationByVariableCommand::new
    );
    commandSuppliers.put(
      createMedianProcessInstanceDurationGroupByVariableWithProcessPartReport().createCommandKey(),
      MedianProcessInstanceDurationGroupByVariableWithProcessPartCommand::new
    );
  }

  private static void addDecisionCountFrequencyReports() {
    commandSuppliers.put(
      createCountFrequencyGroupByNoneReport().createCommandKey(),
      CountDecisionFrequencyGroupByNoneCommand::new
    );
    commandSuppliers.put(
      createCountFrequencyGroupByEvaluationDateTimeReport().createCommandKey(),
      CountDecisionFrequencyGroupByEvaluationDateTimeCommand::new
    );
    commandSuppliers.put(
      createCountFrequencyGroupByInputVariableReport().createCommandKey(),
      CountDecisionFrequencyGroupByInputVariableCommand::new
    );
    commandSuppliers.put(
      createCountFrequencyGroupByOutputVariableReport().createCommandKey(),
      CountDecisionFrequencyGroupByOutputVariableCommand::new
    );
    commandSuppliers.put(
      createCountFrequencyGroupByMatchedRuleReport().createCommandKey(),
      CountDecisionFrequencyGroupByMatchedRuleCommand::new
    );
  }
}
