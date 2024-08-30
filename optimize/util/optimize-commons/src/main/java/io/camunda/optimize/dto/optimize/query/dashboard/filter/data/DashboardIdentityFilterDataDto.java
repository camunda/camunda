/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.dashboard.filter.data;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.IdentityLinkFilterDataDto;
import java.util.List;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
@EqualsAndHashCode(callSuper = true)
public class DashboardIdentityFilterDataDto extends IdentityLinkFilterDataDto {

  protected boolean allowCustomValues;
  protected List<String> defaultValues;

  public DashboardIdentityFilterDataDto(
      final MembershipFilterOperator operator,
      final List<String> values,
      final boolean allowCustomValues) {
    super(operator, values);
    this.allowCustomValues = allowCustomValues;
  }

  public DashboardIdentityFilterDataDto(
      final MembershipFilterOperator operator,
      final List<String> values,
      final boolean allowCustomValues,
      final List<String> defaultValues) {
    super(operator, values);
    this.allowCustomValues = allowCustomValues;
    this.defaultValues = defaultValues;
  }

  public static final class Fields {

    public static final String allowCustomValues = "allowCustomValues";
    public static final String defaultValues = "defaultValues";
  }
}
