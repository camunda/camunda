/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest.analysis;

import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierParametersDto;

import javax.ws.rs.QueryParam;
import java.util.List;

@NoArgsConstructor
public class FlowNodeOutlierParametersRequestDto extends FlowNodeOutlierParametersDto {

  @Override
  @QueryParam("processDefinitionKey")
  public void setProcessDefinitionKey(final String processDefinitionKey) {
    super.setProcessDefinitionKey(processDefinitionKey);
  }

  @Override
  @QueryParam("processDefinitionVersions")
  public void setProcessDefinitionVersions(final List<String> processDefinitionVersions) {
    super.setProcessDefinitionVersions(processDefinitionVersions);
  }

  @Override
  @QueryParam("tenantIds")
  public void setTenantIds(List<String> tenantIds) {
    super.setTenantIds(tenantIds);
  }

  @Override
  @QueryParam("flowNodeId")
  public void setFlowNodeId(final String flowNodeId) {
    super.setFlowNodeId(flowNodeId);
  }

  @Override
  @QueryParam("lowerOutlierBound")
  public void setLowerOutlierBound(final Long lowerOutlierBound) {
    super.setLowerOutlierBound(lowerOutlierBound);
  }

  @Override
  @QueryParam("higherOutlierBound")
  public void setHigherOutlierBound(final Long higherOutlierBound) {
    super.setHigherOutlierBound(higherOutlierBound);
  }

}
