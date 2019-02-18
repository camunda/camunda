package org.camunda.optimize.service.es.report.command.process.util;

import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewOperation;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;


public class ProcessViewDtoCreator {

  public static ProcessViewDto createRawDataView() {
    return new ProcessViewDto(ProcessViewOperation.RAW);
  }

  public static ProcessViewDto createCountFlowNodeFrequencyView() {
    ProcessViewDto view = new ProcessViewDto();
    view.setOperation(ProcessViewOperation.COUNT);
    view.setEntity(ProcessViewEntity.FLOW_NODE);
    view.setProperty(ProcessViewProperty.FREQUENCY);
    return view;
  }

   public static ProcessViewDto createCountProcessInstanceFrequencyView() {
    ProcessViewDto view = new ProcessViewDto();
    view.setOperation(ProcessViewOperation.COUNT);
    view.setEntity(ProcessViewEntity.PROCESS_INSTANCE);
    view.setProperty(ProcessViewProperty.FREQUENCY);
    return view;
  }

  public static ProcessViewDto createAverageProcessInstanceDurationView() {
    ProcessViewDto view = new ProcessViewDto();
    view.setOperation(ProcessViewOperation.AVG);
    view.setEntity(ProcessViewEntity.PROCESS_INSTANCE);
    view.setProperty(ProcessViewProperty.DURATION);
    return view;
  }

  public static ProcessViewDto createMinProcessInstanceDurationView() {
    ProcessViewDto view = new ProcessViewDto();
    view.setOperation(ProcessViewOperation.MIN);
    view.setEntity(ProcessViewEntity.PROCESS_INSTANCE);
    view.setProperty(ProcessViewProperty.DURATION);
    return view;
  }

  public static ProcessViewDto createMaxProcessInstanceDurationView() {
    ProcessViewDto view = new ProcessViewDto();
    view.setOperation(ProcessViewOperation.MAX);
    view.setEntity(ProcessViewEntity.PROCESS_INSTANCE);
    view.setProperty(ProcessViewProperty.DURATION);
    return view;
  }

  public static ProcessViewDto createMedianProcessInstanceDurationView() {
    ProcessViewDto view = new ProcessViewDto();
    view.setOperation(ProcessViewOperation.MEDIAN);
    view.setEntity(ProcessViewEntity.PROCESS_INSTANCE);
    view.setProperty(ProcessViewProperty.DURATION);
    return view;
  }

  public static ProcessViewDto createAverageFlowNodeDurationView() {
    ProcessViewDto view = new ProcessViewDto();
    view.setOperation(ProcessViewOperation.AVG);
    view.setEntity(ProcessViewEntity.FLOW_NODE);
    view.setProperty(ProcessViewProperty.DURATION);
    return view;
  }

  public static ProcessViewDto createMinFlowNodeDurationView() {
    ProcessViewDto view = new ProcessViewDto();
    view.setOperation(ProcessViewOperation.MIN);
    view.setEntity(ProcessViewEntity.FLOW_NODE);
    view.setProperty(ProcessViewProperty.DURATION);
    return view;
  }

  public static ProcessViewDto createMaxFlowNodeDurationView() {
    ProcessViewDto view = new ProcessViewDto();
    view.setOperation(ProcessViewOperation.MAX);
    view.setEntity(ProcessViewEntity.FLOW_NODE);
    view.setProperty(ProcessViewProperty.DURATION);
    return view;
  }

  public static ProcessViewDto createMedianFlowNodeDurationView() {
    ProcessViewDto view = new ProcessViewDto();
    view.setOperation(ProcessViewOperation.MEDIAN);
    view.setEntity(ProcessViewEntity.FLOW_NODE);
    view.setProperty(ProcessViewProperty.DURATION);
    return view;
  }

  public static ProcessViewDto createUserTaskTotalDurationView(final ProcessViewOperation viewOperation) {
    return createUserTaskView(viewOperation, ProcessViewProperty.DURATION);
  }

  public static ProcessViewDto createUserTaskIdleDurationView(final ProcessViewOperation viewOperation) {
    return createUserTaskView(viewOperation, ProcessViewProperty.IDLE_DURATION);
  }

  public static ProcessViewDto createUserTaskWorkDurationView(final ProcessViewOperation viewOperation) {
    return createUserTaskView(viewOperation, ProcessViewProperty.WORK_DURATION);
  }

  private static ProcessViewDto createUserTaskView(final ProcessViewOperation avg,
                                                   final ProcessViewProperty durationProperty) {
    final ProcessViewDto view = new ProcessViewDto();
    view.setOperation(avg);
    view.setEntity(ProcessViewEntity.USER_TASK);
    view.setProperty(durationProperty);
    return view;
  }

}
