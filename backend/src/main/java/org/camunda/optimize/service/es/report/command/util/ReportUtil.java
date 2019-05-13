/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.util;

import lombok.experimental.UtilityClass;
import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionUpdateDto;

@UtilityClass
public class ReportUtil {

  public static void copyDefinitionMetaDataToUpdate(ReportDefinitionDto from, ReportDefinitionUpdateDto to) {
    to.setId(from.getId());
    to.setName(from.getName());
    to.setOwner(from.getOwner());
    to.setLastModifier(from.getLastModifier());
    to.setLastModified(from.getLastModified());
  }

  public static <O extends Combinable> boolean isCombinable(O o1, O o2) {
    if (o1 == null && o2 == null) {
      return true;
    } else if (o1 == null) {
      return false;
    } else if (o2 == null) {
      return false;
    } else {
      return o1.isCombinable(o2);
    }
  }
}
