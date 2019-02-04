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
import org.camunda.optimize.service.es.report.command.decision.frequency.CountDecisionFrequencyGroupByMatchedRuleCommand;
import org.camunda.optimize.service.es.report.command.decision.frequency.CountDecisionFrequencyGroupByNoneCommand;
import org.camunda.optimize.service.es.report.command.decision.frequency.CountDecisionFrequencyGroupByVariableCommand;
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
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMedianFlowNodeDurationGroupByFlowNodeReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMedianProcessInstanceDurationGroupByNoneReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMedianProcessInstanceDurationGroupByNoneWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMedianProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMedianProcessInstanceDurationGroupByStartDateWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMedianProcessInstanceDurationGroupByVariableReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMedianProcessInstanceDurationGroupByVariableWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMinFlowNodeDurationGroupByFlowNodeReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMinProcessInstanceDurationGroupByNoneReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMinProcessInstanceDurationGroupByNoneWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMinProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMinProcessInstanceDurationGroupByStartDateWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMinProcessInstanceDurationGroupByVariableReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createMinProcessInstanceDurationGroupByVariableWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createRawDataReport;
import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.INPUTS;
import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.OUTPUTS;

@Component
public class SingleReportEvaluator {

  private static Map<String, Command> possibleCommands = new HashMap<>();

  static {
    // process reports
    possibleCommands.put(createRawDataReport().createCommandKey(), new RawProcessDataCommand());

    addCountProcessInstanceFrequencyReports();
    addCountFlowNodeFrequencyReports();

    addAverageProcessInstanceDurationReports();
    addMinProcessInstanceDurationReports();
    addMaxProcessInstanceDurationReports();
    addMedianProcessInstanceDurationReports();

    addFlowNodeDurationReports();

    // decision reports
    possibleCommands.put(createRawDecisionDataReport().createCommandKey(), new RawDecisionDataCommand());

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

  private static void addCountProcessInstanceFrequencyReports() {
    possibleCommands.put(
      createCountProcessInstanceFrequencyGroupByNoneReport().createCommandKey(),
      new CountProcessInstanceFrequencyGroupByNoneCommand()
    );
    possibleCommands.put(
      createCountProcessInstanceFrequencyGroupByStartDateReport().createCommandKey(),
      new CountProcessInstanceFrequencyByStartDateCommand()
    );
    possibleCommands.put(
      createCountProcessInstanceFrequencyGroupByVariableReport().createCommandKey(),
      new CountProcessInstanceFrequencyByVariableCommand()
    );
  }

  private static void addCountFlowNodeFrequencyReports() {
    possibleCommands.put(
      createCountFlowNodeFrequencyGroupByFlowNodeReport().createCommandKey(),
      new CountFlowNodeFrequencyByFlowNodeCommand()
    );
  }

  private static void addFlowNodeDurationReports() {
    possibleCommands.put(
      createAverageFlowNodeDurationGroupByFlowNodeReport().createCommandKey(),
      new AverageFlowNodeDurationByFlowNodeCommand()
    );
    possibleCommands.put(
      createMinFlowNodeDurationGroupByFlowNodeReport().createCommandKey(),
      new MinFlowNodeDurationByFlowNodeCommand()
    );
    possibleCommands.put(
      createMaxFlowNodeDurationGroupByFlowNodeReport().createCommandKey(),
      new MaxFlowNodeDurationByFlowNodeCommand()
    );
    possibleCommands.put(
      createMedianFlowNodeDurationGroupByFlowNodeReport().createCommandKey(),
      new MedianFlowNodeDurationByFlowNodeCommand()
    );
  }

  private static void addAverageProcessInstanceDurationReports() {
    possibleCommands.put(
      createAverageProcessInstanceDurationGroupByNoneReport().createCommandKey(),
      new AverageProcessInstanceDurationGroupByNoneCommand()
    );
    possibleCommands.put(
      createAverageProcessInstanceDurationGroupByNoneWithProcessPartReport().createCommandKey(),
      new AverageProcessInstanceDurationGroupByNoneWithProcessPartCommand()
    );
    possibleCommands.put(
      createAverageProcessInstanceDurationGroupByStartDateReport().createCommandKey(),
      new AverageProcessInstanceDurationGroupByStartDateCommand()
    );
    possibleCommands.put(
      createAverageProcessInstanceDurationGroupByStartDateWithProcessPartReport().createCommandKey(),
      new AverageProcessInstanceDurationGroupByStartDateWithProcessPartCommand()
    );
    possibleCommands.put(
      createAverageProcessInstanceDurationGroupByVariableReport().createCommandKey(),
      new AverageProcessInstanceDurationByVariableCommand()
    );
    possibleCommands.put(
      createAverageProcessInstanceDurationGroupByVariableWithProcessPartReport().createCommandKey(),
      new AverageProcessInstanceDurationGroupByVariableWithProcessPartCommand()
    );
  }

  private static void addMinProcessInstanceDurationReports() {
    possibleCommands.put(
      createMinProcessInstanceDurationGroupByNoneReport().createCommandKey(),
      new MinProcessInstanceDurationGroupByNoneCommand()
    );
    possibleCommands.put(
      createMinProcessInstanceDurationGroupByNoneWithProcessPartReport().createCommandKey(),
      new MinProcessInstanceDurationGroupByNoneWithProcessPartCommand()
    );
    possibleCommands.put(
      createMinProcessInstanceDurationGroupByStartDateReport().createCommandKey(),
      new MinProcessInstanceDurationGroupByStartDateCommand()
    );
    possibleCommands.put(
      createMinProcessInstanceDurationGroupByStartDateWithProcessPartReport().createCommandKey(),
      new MinProcessInstanceDurationGroupByStartDateWithProcessPartCommand()
    );
    possibleCommands.put(
      createMinProcessInstanceDurationGroupByVariableReport().createCommandKey(),
      new MinProcessInstanceDurationByVariableCommand()
    );
    possibleCommands.put(
      createMinProcessInstanceDurationGroupByVariableWithProcessPartReport().createCommandKey(),
      new MinProcessInstanceDurationGroupByVariableWithProcessPartCommand()
    );
  }

  private static void addMaxProcessInstanceDurationReports() {
    possibleCommands.put(
      createMaxProcessInstanceDurationGroupByNoneReport().createCommandKey(),
      new MaxProcessInstanceDurationGroupByNoneCommand()
    );
    possibleCommands.put(
      createMaxProcessInstanceDurationGroupByNoneWithProcessPartReport().createCommandKey(),
      new MaxProcessInstanceDurationGroupByNoneWithProcessPartCommand()
    );
    possibleCommands.put(
      createMaxProcessInstanceDurationGroupByStartDateReport().createCommandKey(),
      new MaxProcessInstanceDurationGroupByStartDateCommand()
    );
    possibleCommands.put(
      createMaxProcessInstanceDurationGroupByStartDateWithProcessPartReport().createCommandKey(),
      new MaxProcessInstanceDurationGroupByStartDateWithProcessPartCommand()
    );
    possibleCommands.put(
      createMaxProcessInstanceDurationGroupByVariableReport().createCommandKey(),
      new MaxProcessInstanceDurationByVariableCommand()
    );
    possibleCommands.put(
      createMaxProcessInstanceDurationGroupByVariableWithProcessPartReport().createCommandKey(),
      new MaxProcessInstanceDurationGroupByVariableWithProcessPartCommand()
    );
  }

  private static void addMedianProcessInstanceDurationReports() {
    possibleCommands.put(
      createMedianProcessInstanceDurationGroupByNoneReport().createCommandKey(),
      new MedianProcessInstanceDurationGroupByNoneCommand()
    );
    possibleCommands.put(
      createMedianProcessInstanceDurationGroupByNoneWithProcessPartReport().createCommandKey(),
      new MedianProcessInstanceDurationGroupByNoneWithProcessPartCommand()
    );
    possibleCommands.put(
      createMedianProcessInstanceDurationGroupByStartDateReport().createCommandKey(),
      new MedianProcessInstanceDurationGroupByStartDateCommand()
    );
    possibleCommands.put(
      createMedianProcessInstanceDurationGroupByStartDateWithProcessPartReport().createCommandKey(),
      new MedianProcessInstanceDurationGroupByStartDateWithProcessPartCommand()
    );
    possibleCommands.put(
      createMedianProcessInstanceDurationGroupByVariableReport().createCommandKey(),
      new MedianProcessInstanceDurationByVariableCommand()
    );
    possibleCommands.put(
      createMedianProcessInstanceDurationGroupByVariableWithProcessPartReport().createCommandKey(),
      new MedianProcessInstanceDurationGroupByVariableWithProcessPartCommand()
    );
  }

  private static void addDecisionCountFrequencyReports() {
    possibleCommands.put(
      createCountFrequencyGroupByNoneReport().createCommandKey(),
      new CountDecisionFrequencyGroupByNoneCommand()
    );
    possibleCommands.put(
      createCountFrequencyGroupByEvaluationDateTimeReport().createCommandKey(),
      new CountDecisionFrequencyGroupByEvaluationDateTimeCommand()
    );
    possibleCommands.put(
      createCountFrequencyGroupByInputVariableReport().createCommandKey(),
      new CountDecisionFrequencyGroupByVariableCommand(INPUTS)
    );
    possibleCommands.put(
      createCountFrequencyGroupByOutputVariableReport().createCommandKey(),
      new CountDecisionFrequencyGroupByVariableCommand(OUTPUTS)
    );
    possibleCommands.put(
      createCountFrequencyGroupByMatchedRuleReport().createCommandKey(),
      new CountDecisionFrequencyGroupByMatchedRuleCommand()
    );
  }

  public ReportResult evaluate(ReportDataDto reportData) throws OptimizeException {
    CommandContext commandContext = createCommandContext(reportData);
    Command evaluationCommand = extractCommandWithValidation(reportData);
    return evaluationCommand.evaluate(commandContext);
  }

  private Command extractCommandWithValidation(ReportDataDto reportData) {
    ValidationHelper.validate(reportData);
    return SingleReportEvaluator.this.extractCommand(reportData);
  }

  protected Command extractCommand(ReportDataDto reportData) {
    return possibleCommands.getOrDefault(reportData.createCommandKey(), new NotSupportedCommand());
  }

  protected CommandContext createCommandContext(ReportDataDto reportData) {
    CommandContext commandContext = new CommandContext();
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
}
