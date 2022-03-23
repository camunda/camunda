/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.dashboard.filter.data;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.IdentityLinkFilterDataDto;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants
public class DashboardIdentityFilterDataDto extends IdentityLinkFilterDataDto {

  protected boolean allowCustomValues;
  protected List<String> defaultValues;

  public DashboardIdentityFilterDataDto(final MembershipFilterOperator operator,
                                        final List<String> values,
                                        final boolean allowCustomValues) {
    super(operator, values);
    this.allowCustomValues = allowCustomValues;
  }

  public DashboardIdentityFilterDataDto(final MembershipFilterOperator operator,
                                        final List<String> values,
                                        final boolean allowCustomValues,
                                        final List<String> defaultValues) {
    super(operator, values);
    this.allowCustomValues = allowCustomValues;
    this.defaultValues = defaultValues;
  }

}
