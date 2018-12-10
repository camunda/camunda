package org.camunda.optimize.service.es.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.filter.DecisionQueryFilterEnhancer;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.report.command.Command;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.NotSupportedCommand;
import org.camunda.optimize.service.es.report.command.RawDecisionDataCommand;
import org.camunda.optimize.service.es.report.command.RawProcessDataCommand;
import org.camunda.optimize.service.es.report.command.flownode.duration.AverageFlowNodeDurationByFlowNodeCommand;
import org.camunda.optimize.service.es.report.command.flownode.duration.MaxFlowNodeDurationByFlowNodeCommand;
import org.camunda.optimize.service.es.report.command.flownode.duration.MedianFlowNodeDurationByFlowNodeCommand;
import org.camunda.optimize.service.es.report.command.flownode.duration.MinFlowNodeDurationByFlowNodeCommand;
import org.camunda.optimize.service.es.report.command.flownode.frequency.CountFlowNodeFrequencyByFlowNodeCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.date.withoutprocesspart.AverageProcessInstanceDurationGroupByStartDateCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.date.withoutprocesspart.MaxProcessInstanceDurationGroupByStartDateCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.date.withoutprocesspart.MedianProcessInstanceDurationGroupByStartDateCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.date.withoutprocesspart.MinProcessInstanceDurationGroupByStartDateCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.date.withprocesspart.AverageProcessInstanceDurationGroupByStartDateWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.date.withprocesspart.MaxProcessInstanceDurationGroupByStartDateWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.date.withprocesspart.MedianProcessInstanceDurationGroupByStartDateWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.date.withprocesspart.MinProcessInstanceDurationGroupByStartDateWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.none.withoutprocesspart.AverageProcessInstanceDurationGroupByNoneCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.none.withoutprocesspart.MaxProcessInstanceDurationGroupByNoneCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.none.withoutprocesspart.MedianProcessInstanceDurationGroupByNoneCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.none.withoutprocesspart.MinProcessInstanceDurationGroupByNoneCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.none.withprocesspart.AverageProcessInstanceDurationGroupByNoneWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.none.withprocesspart.MaxProcessInstanceDurationGroupByNoneWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.none.withprocesspart.MedianProcessInstanceDurationGroupByNoneWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.none.withprocesspart.MinProcessInstanceDurationGroupByNoneWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.variable.withoutprocesspart.AverageProcessInstanceDurationByVariableCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.variable.withoutprocesspart.MaxProcessInstanceDurationByVariableCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.variable.withoutprocesspart.MedianProcessInstanceDurationByVariableCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.variable.withoutprocesspart.MinProcessInstanceDurationByVariableCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.variable.withprocesspart.AverageProcessInstanceDurationGroupByVariableWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.variable.withprocesspart.MaxProcessInstanceDurationGroupByVariableWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.variable.withprocesspart.MedianProcessInstanceDurationGroupByVariableWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.pi.duration.groupby.variable.withprocesspart.MinProcessInstanceDurationGroupByVariableWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.pi.frequency.CountProcessInstanceFrequencyByStartDateCommand;
import org.camunda.optimize.service.es.report.command.pi.frequency.CountProcessInstanceFrequencyByVariableCommand;
import org.camunda.optimize.service.es.report.command.pi.frequency.CountProcessInstanceFrequencyGroupByNoneCommand;
import org.camunda.optimize.service.es.report.command.util.ReportUtil;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.ValidationHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.Client;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.util.DecisionReportDataCreator.createRawDecisionDataReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createAverageFlowNodeDurationGroupByFlowNodeReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createAverageProcessInstanceDurationGroupByNoneReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createAverageProcessInstanceDurationGroupByNoneWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createAverageProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createAverageProcessInstanceDurationGroupByStartDateWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createAverageProcessInstanceDurationGroupByVariableReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createAverageProcessInstanceDurationGroupByVariableWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createCountFlowNodeFrequencyGroupByFlowNodeReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createCountProcessInstanceFrequencyGroupByNoneReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createCountProcessInstanceFrequencyGroupByStartDateReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createCountProcessInstanceFrequencyGroupByVariableReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createMaxFlowNodeDurationGroupByFlowNodeReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createMaxProcessInstanceDurationGroupByNoneReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createMaxProcessInstanceDurationGroupByNoneWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createMaxProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createMaxProcessInstanceDurationGroupByStartDateWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createMaxProcessInstanceDurationGroupByVariableReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createMaxProcessInstanceDurationGroupByVariableWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createMedianFlowNodeDurationGroupByFlowNodeReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createMedianProcessInstanceDurationGroupByNoneReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createMedianProcessInstanceDurationGroupByNoneWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createMedianProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createMedianProcessInstanceDurationGroupByStartDateWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createMedianProcessInstanceDurationGroupByVariableReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createMedianProcessInstanceDurationGroupByVariableWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createMinFlowNodeDurationGroupByFlowNodeReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createMinProcessInstanceDurationGroupByNoneReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createMinProcessInstanceDurationGroupByNoneWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createMinProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createMinProcessInstanceDurationGroupByStartDateWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createMinProcessInstanceDurationGroupByVariableReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createMinProcessInstanceDurationGroupByVariableWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.util.ProcessReportDataCreator.createRawDataReport;

@Component
public class ReportEvaluator {

  private static Map<String, Command> possibleCommands = new HashMap<>();

  static {
    possibleCommands.put(createRawDataReport().createCommandKey(), new RawProcessDataCommand());
    possibleCommands.put(createRawDecisionDataReport().createCommandKey(), new RawDecisionDataCommand());

    addCountProcessInstanceFrequencyReports();
    addCountFlowNodeFrequencyReports();

    addAverageProcessInstanceDurationReports();
    addMinProcessInstanceDurationReports();
    addMaxProcessInstanceDurationReports();
    addMedianProcessInstanceDurationReports();

    addFlowNodeDurationReports();
  }

  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private ProcessQueryFilterEnhancer processQueryFilterEnhancer;
  @Autowired
  private DecisionQueryFilterEnhancer decisionQueryFilterEnhancer;
  @Autowired
  private Client esclient;

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

  public ReportResultDto evaluate(ReportDataDto reportData) throws OptimizeException {
    CommandContext commandContext = createCommandContext(reportData);
    Command evaluationCommand = extractCommand(reportData);
    ReportResultDto result = evaluationCommand.evaluate(commandContext);
    ReportUtil.copyReportData(reportData, result);
    return result;
  }

  private Command extractCommand(ReportDataDto reportData) {
    ValidationHelper.validate(reportData);
    return possibleCommands.getOrDefault(reportData.createCommandKey(), new NotSupportedCommand());
  }

  private CommandContext createCommandContext(ReportDataDto reportData) {
    CommandContext commandContext = new CommandContext();
    commandContext.setConfigurationService(configurationService);
    commandContext.setEsclient(esclient);
    commandContext.setObjectMapper(objectMapper);
    if (reportData instanceof ProcessReportDataDto) {
      commandContext.setQueryFilterEnhancer(processQueryFilterEnhancer);
    } else if (reportData instanceof DecisionReportDataDto) {
      commandContext.setQueryFilterEnhancer(decisionQueryFilterEnhancer);
    }
    commandContext.setReportData(reportData);
    return commandContext;
  }

}
