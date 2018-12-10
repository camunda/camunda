package org.camunda.optimize.service.es.report.command.util;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.parameters.ProcessPartDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;

import static org.camunda.optimize.service.es.report.command.util.ProcessGroupByDtoCreator.createGroupByFlowNode;
import static org.camunda.optimize.service.es.report.command.util.ProcessGroupByDtoCreator.createGroupByNone;
import static org.camunda.optimize.service.es.report.command.util.ProcessGroupByDtoCreator.createGroupByStartDateDto;
import static org.camunda.optimize.service.es.report.command.util.ProcessGroupByDtoCreator.createGroupByVariable;
import static org.camunda.optimize.service.es.report.command.util.ProcessViewDtoCreator.createAverageFlowNodeDurationView;
import static org.camunda.optimize.service.es.report.command.util.ProcessViewDtoCreator.createAverageProcessInstanceDurationView;
import static org.camunda.optimize.service.es.report.command.util.ProcessViewDtoCreator.createCountFlowNodeFrequencyView;
import static org.camunda.optimize.service.es.report.command.util.ProcessViewDtoCreator.createCountProcessInstanceFrequencyView;
import static org.camunda.optimize.service.es.report.command.util.ProcessViewDtoCreator.createMaxFlowNodeDurationView;
import static org.camunda.optimize.service.es.report.command.util.ProcessViewDtoCreator.createMaxProcessInstanceDurationView;
import static org.camunda.optimize.service.es.report.command.util.ProcessViewDtoCreator.createMedianFlowNodeDurationView;
import static org.camunda.optimize.service.es.report.command.util.ProcessViewDtoCreator.createMedianProcessInstanceDurationView;
import static org.camunda.optimize.service.es.report.command.util.ProcessViewDtoCreator.createMinFlowNodeDurationView;
import static org.camunda.optimize.service.es.report.command.util.ProcessViewDtoCreator.createMinProcessInstanceDurationView;
import static org.camunda.optimize.service.es.report.command.util.ProcessViewDtoCreator.createRawDataView;

public class ProcessReportDataCreator {

  public static ProcessReportDataDto createAverageFlowNodeDurationGroupByFlowNodeReport() {
    ProcessViewDto view = createAverageFlowNodeDurationView();
    ProcessGroupByDto groupByDto = createGroupByFlowNode();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createMinFlowNodeDurationGroupByFlowNodeReport() {
    ProcessViewDto view = createMinFlowNodeDurationView();
    ProcessGroupByDto groupByDto = createGroupByFlowNode();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createMaxFlowNodeDurationGroupByFlowNodeReport() {
    ProcessViewDto view = createMaxFlowNodeDurationView();
    ProcessGroupByDto groupByDto = createGroupByFlowNode();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createMedianFlowNodeDurationGroupByFlowNodeReport() {
    ProcessViewDto view = createMedianFlowNodeDurationView();
    ProcessGroupByDto groupByDto = createGroupByFlowNode();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createAverageProcessInstanceDurationGroupByNoneReport() {
    ProcessViewDto view = createAverageProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByNone();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createAverageProcessInstanceDurationGroupByNoneWithProcessPartReport() {
    ProcessViewDto view = createAverageProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByNone();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.getParameters().setProcessPart(new ProcessPartDto());
    return reportData;
  }

  public static ProcessReportDataDto createMinProcessInstanceDurationGroupByNoneReport() {
    ProcessViewDto view = createMinProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByNone();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createMinProcessInstanceDurationGroupByNoneWithProcessPartReport() {
    ProcessViewDto view = createMinProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByNone();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.getParameters().setProcessPart(new ProcessPartDto());
    return reportData;
  }

  public static ProcessReportDataDto createMaxProcessInstanceDurationGroupByNoneReport() {
    ProcessViewDto view = createMaxProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByNone();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createMaxProcessInstanceDurationGroupByNoneWithProcessPartReport() {
    ProcessViewDto view = createMaxProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByNone();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.getParameters().setProcessPart(new ProcessPartDto());
    return reportData;
  }

  public static ProcessReportDataDto createMedianProcessInstanceDurationGroupByNoneReport() {
    ProcessViewDto view = createMedianProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByNone();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createMedianProcessInstanceDurationGroupByNoneWithProcessPartReport() {
    ProcessViewDto view = createMedianProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByNone();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.getParameters().setProcessPart(new ProcessPartDto());
    return reportData;
  }

  public static ProcessReportDataDto createAverageProcessInstanceDurationGroupByStartDateReport() {
    ProcessViewDto view = createAverageProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByStartDateDto();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createAverageProcessInstanceDurationGroupByStartDateWithProcessPartReport() {
    ProcessViewDto view = createAverageProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByStartDateDto();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.getParameters().setProcessPart(new ProcessPartDto());
    return reportData;
  }

  public static ProcessReportDataDto createMinProcessInstanceDurationGroupByStartDateReport() {
    ProcessViewDto view = createMinProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByStartDateDto();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createMinProcessInstanceDurationGroupByStartDateWithProcessPartReport() {
    ProcessViewDto view = createMinProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByStartDateDto();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.getParameters().setProcessPart(new ProcessPartDto());
    return reportData;
  }

  public static ProcessReportDataDto createMaxProcessInstanceDurationGroupByStartDateReport() {
    ProcessViewDto view = createMaxProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByStartDateDto();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createMaxProcessInstanceDurationGroupByStartDateWithProcessPartReport() {
    ProcessViewDto view = createMaxProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByStartDateDto();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.getParameters().setProcessPart(new ProcessPartDto());
    return reportData;
  }

  public static ProcessReportDataDto createMedianProcessInstanceDurationGroupByStartDateReport() {
    ProcessViewDto view = createMedianProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByStartDateDto();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createMedianProcessInstanceDurationGroupByStartDateWithProcessPartReport() {
    ProcessViewDto view = createMedianProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByStartDateDto();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.getParameters().setProcessPart(new ProcessPartDto());
    return reportData;
  }

  public static ProcessReportDataDto createAverageProcessInstanceDurationGroupByVariableReport() {
    ProcessViewDto view = createAverageProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByVariable();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createAverageProcessInstanceDurationGroupByVariableWithProcessPartReport() {
    ProcessReportDataDto reportData = createAverageProcessInstanceDurationGroupByVariableReport();
    reportData.getParameters().setProcessPart(new ProcessPartDto());
    return reportData;
  }

  public static ProcessReportDataDto createMinProcessInstanceDurationGroupByVariableReport() {
    ProcessViewDto view = createMinProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByVariable();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createMinProcessInstanceDurationGroupByVariableWithProcessPartReport() {
    ProcessReportDataDto reportData = createMinProcessInstanceDurationGroupByVariableReport();
    reportData.getParameters().setProcessPart(new ProcessPartDto());
    return reportData;
  }

  public static ProcessReportDataDto createMaxProcessInstanceDurationGroupByVariableReport() {
    ProcessViewDto view = createMaxProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByVariable();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createMaxProcessInstanceDurationGroupByVariableWithProcessPartReport() {
    ProcessReportDataDto reportData = createMaxProcessInstanceDurationGroupByVariableReport();
    reportData.getParameters().setProcessPart(new ProcessPartDto());
    return reportData;
  }

  public static ProcessReportDataDto createMedianProcessInstanceDurationGroupByVariableReport() {
    ProcessViewDto view = createMedianProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByVariable();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createMedianProcessInstanceDurationGroupByVariableWithProcessPartReport() {
    ProcessReportDataDto reportData = createMedianProcessInstanceDurationGroupByVariableReport();
    reportData.getParameters().setProcessPart(new ProcessPartDto());
    return reportData;
  }

  public static ProcessReportDataDto createRawDataReport() {
    ProcessViewDto view = createRawDataView();
    ProcessGroupByDto groupByDto = createGroupByNone();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createCountProcessInstanceFrequencyGroupByNoneReport() {
    ProcessViewDto view = createCountProcessInstanceFrequencyView();
    ProcessGroupByDto groupByDto = createGroupByNone();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createCountProcessInstanceFrequencyGroupByStartDateReport() {
    ProcessViewDto view = createCountProcessInstanceFrequencyView();
    ProcessGroupByDto groupByDto = createGroupByStartDateDto();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createCountProcessInstanceFrequencyGroupByVariableReport() {
    ProcessViewDto view = createCountProcessInstanceFrequencyView();
    ProcessGroupByDto groupByDto = createGroupByVariable();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createCountFlowNodeFrequencyGroupByFlowNodeReport() {
    ProcessViewDto view = createCountFlowNodeFrequencyView();
    ProcessGroupByDto groupByDto = createGroupByFlowNode();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }


}
