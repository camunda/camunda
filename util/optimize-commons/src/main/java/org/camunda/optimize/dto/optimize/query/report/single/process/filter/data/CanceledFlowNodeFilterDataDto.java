/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.data;

import lombok.Data;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;

import java.util.List;

@Data
public class CanceledFlowNodeFilterDataDto implements FilterDataDto {

  protected List<String> values;

}
