package org.camunda.optimize.service.es.report.command.util;

import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ViewDto;
import org.camunda.optimize.dto.optimize.query.report.group.FlowNodesGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.group.GroupByDto;
import org.camunda.optimize.dto.optimize.query.report.group.NoneGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.group.StartDateGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.group.value.StartDateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.group.value.VariableGroupByValueDto;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.DATE_UNIT_DAY;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.HEAT_VISUALIZATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.SINGLE_NUMBER_VISUALIZATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.TABLE_VISUALIZATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_AVERAGE_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_COUNT_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_DURATION_PROPERTY;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_FLOW_NODE_ENTITY;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_FREQUENCY_PROPERTY;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_MAX_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_MEDIAN_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_MIN_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_PROCESS_INSTANCE_ENTITY;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_RAW_DATA_OPERATION;


public class ViewDtoCreator {

  public static ViewDto createRawDataView() {
    return new ViewDto(VIEW_RAW_DATA_OPERATION);
  }

  public static ViewDto createCountFlowNodeFrequencyView() {
    ViewDto view = new ViewDto();
    view.setOperation(VIEW_COUNT_OPERATION);
    view.setEntity(VIEW_FLOW_NODE_ENTITY);
    view.setProperty(VIEW_FREQUENCY_PROPERTY);
    return view;
  }

   public static ViewDto createCountProcessInstanceFrequencyView() {
    ViewDto view = new ViewDto();
    view.setOperation(VIEW_COUNT_OPERATION);
    view.setEntity(VIEW_PROCESS_INSTANCE_ENTITY);
    view.setProperty(VIEW_FREQUENCY_PROPERTY);
    return view;
  }

  public static ViewDto createAverageProcessInstanceDurationView() {
    ViewDto view = new ViewDto();
    view.setOperation(VIEW_AVERAGE_OPERATION);
    view.setEntity(VIEW_PROCESS_INSTANCE_ENTITY);
    view.setProperty(VIEW_DURATION_PROPERTY);
    return view;
  }

  public static ViewDto createAverageFlowNodeDurationView() {
    ViewDto view = new ViewDto();
    view.setOperation(VIEW_AVERAGE_OPERATION);
    view.setEntity(VIEW_FLOW_NODE_ENTITY);
    view.setProperty(VIEW_DURATION_PROPERTY);
    return view;
  }

  public static ViewDto createMinFlowNodeDurationView() {
    ViewDto view = new ViewDto();
    view.setOperation(VIEW_MIN_OPERATION);
    view.setEntity(VIEW_FLOW_NODE_ENTITY);
    view.setProperty(VIEW_DURATION_PROPERTY);
    return view;
  }

  public static ViewDto createMaxFlowNodeDurationView() {
    ViewDto view = new ViewDto();
    view.setOperation(VIEW_MAX_OPERATION);
    view.setEntity(VIEW_FLOW_NODE_ENTITY);
    view.setProperty(VIEW_DURATION_PROPERTY);
    return view;
  }

  public static ViewDto createMedianFlowNodeDurationView() {
    ViewDto view = new ViewDto();
    view.setOperation(VIEW_MEDIAN_OPERATION);
    view.setEntity(VIEW_FLOW_NODE_ENTITY);
    view.setProperty(VIEW_DURATION_PROPERTY);
    return view;
  }
}
