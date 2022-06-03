/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.optimize;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisRequestDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisResponseDto;
import org.camunda.optimize.dto.optimize.query.analysis.DurationChartEntryDto;
import org.camunda.optimize.dto.optimize.query.analysis.FindingsDto;
import org.camunda.optimize.dto.optimize.query.analysis.VariableTermDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;

@AllArgsConstructor
public class AnalysisClient {
  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public Response getProcessDefinitionCorrelationRawResponse(BranchAnalysisRequestDto branchAnalysisRequestDto) {
    return getProcessDefinitionCorrelationRawResponseAsUser(
      branchAnalysisRequestDto,
      DEFAULT_USERNAME,
      DEFAULT_USERNAME
    );
  }

  public Response getProcessDefinitionCorrelationRawResponseAsUser(BranchAnalysisRequestDto branchAnalysisRequestDto,
                                                                   String username, String password) {
    return getRequestExecutor()
      .buildProcessDefinitionCorrelation(branchAnalysisRequestDto)
      .withUserAuthentication(username, password)
      .execute();
  }

  public Response getProcessDefinitionCorrelationRawResponseWithoutAuth(BranchAnalysisRequestDto branchAnalysisRequestDto) {
    return getRequestExecutor()
      .buildProcessDefinitionCorrelation(branchAnalysisRequestDto)
      .withoutAuthentication()
      .execute();
  }

  public BranchAnalysisResponseDto getProcessDefinitionCorrelation(BranchAnalysisRequestDto branchAnalysisRequestDto) {
    return getRequestExecutor()
      .buildProcessDefinitionCorrelation(branchAnalysisRequestDto)
      .execute(BranchAnalysisResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public BranchAnalysisRequestDto createAnalysisDto(final String processDefinitionKey,
                                                    final List<String> processDefinitionVersion,
                                                    final List<String> tenantIds,
                                                    String splittingGateway,
                                                    String endEvent) {
    BranchAnalysisRequestDto dto = new BranchAnalysisRequestDto();
    dto.setProcessDefinitionKey(processDefinitionKey);
    dto.setProcessDefinitionVersions(processDefinitionVersion);
    dto.setTenantIds(tenantIds);
    dto.setGateway(splittingGateway);
    dto.setEnd(endEvent);
    return dto;
  }

  public BranchAnalysisResponseDto performBranchAnalysis(final String processDefinitionKey,
                                                         final List<String> processDefinitionVersions,
                                                         final List<String> tenantIds,
                                                         final String gatewayID,
                                                         final String endEventId) {
    BranchAnalysisRequestDto dto = createAnalysisDto(
      processDefinitionKey,
      processDefinitionVersions,
      tenantIds,
      gatewayID,
      endEventId
    );
    return getProcessDefinitionCorrelation(dto);
  }

  public HashMap<String, FindingsDto> getFlowNodeOutliers(String procDefKey, List<String> procDefVersions,
                                                          List<String> tenants, long minimalDeviationInMs,
                                                          boolean onlyHumanTasks) {
    return getRequestExecutor()
      .buildFlowNodeOutliersRequest(procDefKey, procDefVersions, tenants, minimalDeviationInMs, onlyHumanTasks)
      .execute(new TypeReference<>() {
      });
  }

  public HashMap<String, FindingsDto> getFlowNodeOutliers(String procDefKey, List<String> procDefVersions,
                                                          List<String> tenants) {
    return getFlowNodeOutliers(procDefKey, procDefVersions, tenants, 0, false);
  }

  public List<DurationChartEntryDto> getDurationChart(String procDefKey, List<String> procDefVersions,
                                                      List<String> tenants, String flowNodeId) {
    return getDurationChart(procDefKey, procDefVersions, tenants, flowNodeId, null, null);
  }

  public List<DurationChartEntryDto> getDurationChart(String procDefKey, List<String> procDefVersions,
                                                      List<String> tenants, String flowNodeId, Long lowerOutlierBound,
                                                      Long higherOutlierBound) {
    return getRequestExecutor()
      .buildFlowNodeDurationChartRequest(
        procDefKey,
        procDefVersions,
        flowNodeId,
        tenants,
        lowerOutlierBound,
        higherOutlierBound
      )
      .executeAndReturnList(DurationChartEntryDto.class, Response.Status.OK.getStatusCode());
  }

  public List<VariableTermDto> getVariableTermDtosActivity(long sampleOutliersHigherOutlierBound,
                                                           String key, List<String> versions,
                                                           List<String> tenantIds, String flowNodeId,
                                                           Long lowerOutlierBound) {
    Response variableTermDtosActivityRawResponse = getVariableTermDtosActivityRawResponse(
      sampleOutliersHigherOutlierBound,
      key,
      versions,
      tenantIds,
      flowNodeId,
      lowerOutlierBound
    );
    assertThat(variableTermDtosActivityRawResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    String jsonString = variableTermDtosActivityRawResponse.readEntity(String.class);
    try {
      return new ObjectMapper().readValue(jsonString, new TypeReference<>() {
      });
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  public Response getVariableTermDtosActivityRawResponse(long sampleOutliersHigherOutlierBound, String key,
                                                         List<String> versions, List<String> tenantIds,
                                                         String flowNodeId,
                                                         Long lowerOutlierBound) {
    return getRequestExecutor()
      .buildSignificantOutlierVariableTermsRequest(
        key,
        versions,
        tenantIds,
        flowNodeId,
        lowerOutlierBound,
        sampleOutliersHigherOutlierBound
      )
      .execute();
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
