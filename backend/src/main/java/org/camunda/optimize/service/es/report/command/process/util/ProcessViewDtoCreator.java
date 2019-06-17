/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.util;

import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;


public class ProcessViewDtoCreator {

  public static ProcessViewDto createRawDataView() {
    return new ProcessViewDto(ProcessViewProperty.RAW_DATA);
  }

  public static ProcessViewDto createCountFlowNodeFrequencyView() {
    ProcessViewDto view = new ProcessViewDto();
    view.setEntity(ProcessViewEntity.FLOW_NODE);
    view.setProperty(ProcessViewProperty.FREQUENCY);
    return view;
  }

   public static ProcessViewDto createCountProcessInstanceFrequencyView() {
    ProcessViewDto view = new ProcessViewDto();
    view.setEntity(ProcessViewEntity.PROCESS_INSTANCE);
    view.setProperty(ProcessViewProperty.FREQUENCY);
    return view;
  }

  public static ProcessViewDto createProcessInstanceDurationView() {
    ProcessViewDto view = new ProcessViewDto();
    view.setEntity(ProcessViewEntity.PROCESS_INSTANCE);
    view.setProperty(ProcessViewProperty.DURATION);
    return view;
  }

  public static ProcessViewDto createFlowNodeDurationView() {
    ProcessViewDto view = new ProcessViewDto();
    view.setEntity(ProcessViewEntity.FLOW_NODE);
    view.setProperty(ProcessViewProperty.DURATION);
    return view;
  }

  public static ProcessViewDto createUserTaskDurationView() {
    final ProcessViewDto view = new ProcessViewDto();
    view.setEntity(ProcessViewEntity.USER_TASK);
    view.setProperty(ProcessViewProperty.DURATION);
    return view;
  }

}
