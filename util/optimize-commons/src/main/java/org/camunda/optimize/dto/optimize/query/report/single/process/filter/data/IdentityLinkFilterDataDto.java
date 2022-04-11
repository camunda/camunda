/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.data;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;

import java.util.List;

@AllArgsConstructor
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdentityLinkFilterDataDto implements FilterDataDto {

  protected MembershipFilterOperator operator;
  protected List<String> values;

}
