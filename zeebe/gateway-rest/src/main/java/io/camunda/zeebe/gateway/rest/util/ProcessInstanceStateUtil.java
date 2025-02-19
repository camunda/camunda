/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.util;

import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.zeebe.gateway.protocol.rest.AdvancedProcessInstanceStateFilter;
import io.camunda.zeebe.gateway.protocol.rest.AdvancedStringFilter;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceStateEnum;
import io.camunda.zeebe.gateway.protocol.rest.StringFilterProperty;

public class ProcessInstanceStateUtil {

  public static StringFilterProperty toInternalProcessInstanceStateFilter(
      AdvancedProcessInstanceStateFilter filterProperty) {

    final AdvancedStringFilter stringFilterProperty = new AdvancedStringFilter();
    stringFilterProperty.set$Eq(toInternalStateAsString(filterProperty.get$Eq()));
    stringFilterProperty.set$Neq(toInternalStateAsString(filterProperty.get$Neq()));
    stringFilterProperty.set$Exists(filterProperty.get$Exists());
    stringFilterProperty.set$Like(filterProperty.get$Like());
    filterProperty
        .get$In()
        .forEach(item -> stringFilterProperty.add$InItem(toInternalStateAsString(item)));

    return stringFilterProperty;
  }

  public static String toInternalStateAsString(ProcessInstanceStateEnum processInstanceStateEnum) {
    final ProcessInstanceEntity.ProcessInstanceState internalState =
        toInternalState(processInstanceStateEnum);
    return (internalState == null) ? null : internalState.name();
  }

  public static ProcessInstanceEntity.ProcessInstanceState toInternalState(
      ProcessInstanceStateEnum processInstanceStateEnum) {
    if (processInstanceStateEnum == null) {
      return null;
    }
    if (processInstanceStateEnum == ProcessInstanceStateEnum.TERMINATED) {
      return ProcessInstanceEntity.ProcessInstanceState.CANCELED;
    }

    return ProcessInstanceEntity.ProcessInstanceState.valueOf(processInstanceStateEnum.name());
  }
}
