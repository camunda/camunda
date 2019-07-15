/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

package org.camunda.optimize.dto.optimize.query.definition;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;

import java.util.Comparator;
import java.util.List;

import static java.util.Comparator.naturalOrder;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.LATEST_VERSION;

@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
public class DefinitionAvailableVersionsWithTenants {

  private static final String VERSION_ORDER = String.join(LATEST_VERSION, " ", ALL_VERSIONS);
  
  @NonNull
  private String key;
  private String name;
  @NonNull
  private List<DefinitionVersions> versions;
  private List<TenantDto> tenants;

  public void sort() {
    versions.sort(
      Comparator.comparing(
        DefinitionVersions::getVersion, (o1, o2) -> {
          if (StringUtils.isNumeric(o1) && StringUtils.isNumeric(o2)) {
            return Long.valueOf(o1).compareTo(Long.valueOf(o2));
          } else {
            // ensures that the order is numeric values, latest and then all versions
            return Integer.compare(VERSION_ORDER.indexOf(o1), VERSION_ORDER.indexOf(o2));
          }
        }
      ).reversed()
    );
    tenants.sort(Comparator.comparing(TenantDto::getId, Comparator.nullsFirst(naturalOrder())));
  }
}
