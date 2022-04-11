/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.variable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;
import lombok.Data;
import org.camunda.optimize.service.util.TenantListHandlingUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;

@Data
public class ProcessVariableValueRequestDto {

  private String processInstanceId;
  private String processDefinitionKey;
  private List<String> processDefinitionVersions = new ArrayList<>();
  private List<String> tenantIds = new ArrayList<>(Collections.singletonList(null));
  private String name;
  private VariableType type;
  private String valueFilter;
  private Integer resultOffset = 0;
  private Integer numResults = MAX_RESPONSE_SIZE_LIMIT;

  @JsonIgnore
  public void setProcessDefinitionVersion(String processDefinitionVersion) {
    this.processDefinitionVersions = Lists.newArrayList(processDefinitionVersion);
  }

  public List<String> getTenantIds() {
    return TenantListHandlingUtil.sortAndReturnTenantIdList(tenantIds);
  }

}
