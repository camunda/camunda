package org.camunda.optimize.service.es.report.command.util;

import org.camunda.optimize.dto.optimize.query.report.ViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.processpart.ProcessPartDto;

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

public class ReportDataCreator {

  public static SingleReportDataDto createAverageFlowNodeDurationGroupByFlowNodeReport() {
    ViewDto view = createAverageFlowNodeDurationView();
    GroupByDto groupByDto = createGroupByFlowNode();

    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static SingleReportDataDto createMinFlowNodeDurationGroupByFlowNodeReport() {
    ViewDto view = createMinFlowNodeDurationView();
    GroupByDto groupByDto = createGroupByFlowNode();

    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static SingleReportDataDto createMaxFlowNodeDurationGroupByFlowNodeReport() {
    ViewDto view = createMaxFlowNodeDurationView();
    GroupByDto groupByDto = createGroupByFlowNode();

    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static SingleReportDataDto createMedianFlowNodeDurationGroupByFlowNodeReport() {
    ViewDto view = createMedianFlowNodeDurationView();
    GroupByDto groupByDto = createGroupByFlowNode();

    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static SingleReportDataDto createAverageProcessInstanceDurationGroupByNoneReport() {
    ViewDto view = createAverageProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByNone();

    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static SingleReportDataDto createAverageProcessInstanceDurationGroupByNoneWithProcessPartReport() {
    ViewDto view = createAverageProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByNone();

    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.setProcessPart(new ProcessPartDto());
    return reportData;
  }

  public static SingleReportDataDto createMinProcessInstanceDurationGroupByNoneReport() {
    ViewDto view = createMinProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByNone();

    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static SingleReportDataDto createMinProcessInstanceDurationGroupByNoneWithProcessPartReport() {
    ViewDto view = createMinProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByNone();

    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.setProcessPart(new ProcessPartDto());
    return reportData;
  }

  public static SingleReportDataDto createMaxProcessInstanceDurationGroupByNoneReport() {
    ViewDto view = createMaxProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByNone();

    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static SingleReportDataDto createMaxProcessInstanceDurationGroupByNoneWithProcessPartReport() {
    ViewDto view = createMaxProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByNone();

    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.setProcessPart(new ProcessPartDto());
    return reportData;
  }

  public static SingleReportDataDto createMedianProcessInstanceDurationGroupByNoneReport() {
    ViewDto view = createMedianProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByNone();

    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static SingleReportDataDto createMedianProcessInstanceDurationGroupByNoneWithProcessPartReport() {
    ViewDto view = createMedianProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByNone();

    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.setProcessPart(new ProcessPartDto());
    return reportData;
  }

  public static SingleReportDataDto createAverageProcessInstanceDurationGroupByStartDateReport() {
    ViewDto view = createAverageProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByStartDateDto();

    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static SingleReportDataDto createMinProcessInstanceDurationGroupByStartDateReport() {
    ViewDto view = createMinProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByStartDateDto();

    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static SingleReportDataDto createMaxProcessInstanceDurationGroupByStartDateReport() {
    ViewDto view = createMaxProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByStartDateDto();

    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static SingleReportDataDto createMedianProcessInstanceDurationGroupByStartDateReport() {
    ViewDto view = createMedianProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByStartDateDto();

    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static SingleReportDataDto createAverageProcessInstanceDurationGroupByVariableReport() {
    ViewDto view = createAverageProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByVariable();

    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static SingleReportDataDto createMinProcessInstanceDurationGroupByVariableReport() {
    ViewDto view = createMinProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByVariable();

    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static SingleReportDataDto createMaxProcessInstanceDurationGroupByVariableReport() {
    ViewDto view = createMaxProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByVariable();

    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static SingleReportDataDto createMedianProcessInstanceDurationGroupByVariableReport() {
    ViewDto view = createMedianProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByVariable();

    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static SingleReportDataDto createRawDataReport() {
    ViewDto view = createRawDataView();
    GroupByDto groupByDto = createGroupByNone();

    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static SingleReportDataDto createCountProcessInstanceFrequencyGroupByNoneReport() {
    ViewDto view = createCountProcessInstanceFrequencyView();
    GroupByDto groupByDto = createGroupByNone();

    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static SingleReportDataDto createCountProcessInstanceFrequencyGroupByStartDateReport() {
    ViewDto view = createCountProcessInstanceFrequencyView();
    GroupByDto groupByDto = createGroupByStartDateDto();

    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static SingleReportDataDto createCountProcessInstanceFrequencyGroupByVariableReport() {
    ViewDto view = createCountProcessInstanceFrequencyView();
    GroupByDto groupByDto = createGroupByVariable();

    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static SingleReportDataDto createCountFlowNodeFrequencyGroupByFlowNodeReport() {
    ViewDto view = createCountFlowNodeFrequencyView();
    GroupByDto groupByDto = createGroupByFlowNode();

    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }


}
