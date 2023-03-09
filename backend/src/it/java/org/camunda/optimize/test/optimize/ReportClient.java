/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.optimize;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationRequestDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedCombinedReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedDecisionReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedSingleReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_LOCALE;
import static org.camunda.optimize.service.util.ProcessReportDataBuilderHelper.createCombinedReportData;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;

@AllArgsConstructor
public class ReportClient {

  private static final String RANDOM_STRING = "something";
  private static final String TEST_REPORT_NAME = "My test report";

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public String createCombinedReport(String collectionId, List<String> singleReportIds) {
    CombinedReportDefinitionRequestDto report = new CombinedReportDefinitionRequestDto();
    report.setCollectionId(collectionId);
    report.setData(createCombinedReportData(singleReportIds.toArray(new String[]{})));
    return createNewCombinedReport(report);
  }

  public String createEmptyCombinedReport(final String collectionId) {
    return createCombinedReport(collectionId, Collections.emptyList());
  }

  public String createNewCombinedReport(String... singleReportIds) {
    CombinedReportDefinitionRequestDto report = new CombinedReportDefinitionRequestDto();
    report.setData(createCombinedReportData(singleReportIds));
    return createNewCombinedReport(report);
  }

  public void updateCombinedReport(final String combinedReportId, final List<String> containedReportIds) {
    final CombinedReportDefinitionRequestDto combinedReportData = new CombinedReportDefinitionRequestDto();
    combinedReportData.getData()
      .getReports()
      .addAll(
        containedReportIds.stream()
          .map(CombinedReportItemDto::new)
          .collect(Collectors.toList())
      );
    getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(combinedReportId, combinedReportData)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .execute();
  }

  public Response updateCombinedReport(final String combinedReportId, final ReportDefinitionDto combinedReportData,
                                       String username, String password) {
    return getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(combinedReportId, combinedReportData)
      .withUserAuthentication(username, password)
      .execute();
  }

  public Response updateSingleProcessReport(final String reportId, final ReportDefinitionDto updatedReport) {
    return updateSingleProcessReport(reportId, updatedReport, false, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public Response updateSingleProcessReport(final String reportId, final ReportDefinitionDto updatedReport,
                                            Boolean force, String username, String password) {
    return getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(reportId, updatedReport, force)
      .withUserAuthentication(username, password)
      .execute();
  }

  public Response updateDecisionReport(final String reportId, final ReportDefinitionDto updatedReport) {
    return updateDecisionReport(reportId, updatedReport, false, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public Response updateDecisionReport(final String reportId, final ReportDefinitionDto updatedReport,
                                       Boolean force, String username, String password) {
    return getRequestExecutor()
      .withUserAuthentication(username, password)
      .buildUpdateSingleDecisionReportRequest(reportId, updatedReport, force)
      .execute();
  }

  public Response createNewCombinedReportAsUserRawResponse(String collectionId, List<String> singleReportIds,
                                                           String username, String password) {
    CombinedReportDefinitionRequestDto report = new CombinedReportDefinitionRequestDto();
    report.setCollectionId(collectionId);
    report.setData(createCombinedReportData(singleReportIds.toArray(new String[]{})));
    return getRequestExecutor()
      .buildCreateCombinedReportRequest(report)
      .withUserAuthentication(username, password)
      .execute();
  }

  public String createNewCombinedReport(CombinedReportDefinitionRequestDto combinedReportDefinitionDto) {
    return getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReportDefinitionDto)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  public String createSingleReport(final String collectionId, final DefinitionType definitionType,
                                   final String definitionKey, final List<String> tenants) {
    switch (definitionType) {
      case PROCESS:
        return createAndStoreProcessReport(collectionId, definitionKey, tenants);
      case DECISION:
        return createAndStoreDecisionReport(collectionId, definitionKey, tenants);
      default:
        throw new IllegalStateException("Uncovered definitionType: " + definitionType);
    }
  }

  public String createAndStoreProcessReport(String collectionId, String definitionKey, List<String> tenants) {
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto = createSingleProcessReportDefinitionDto(
      collectionId,
      definitionKey,
      tenants
    );
    return createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  public String createAndStoreProcessReport(final String definitionKey, final List<String> tenants) {
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto = createSingleProcessReportDefinitionDto(
      null,
      definitionKey,
      tenants
    );
    return createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  public String createAndStoreProcessReport(final String collectionId, final String definitionKey) {
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto = createSingleProcessReportDefinitionDto(
      collectionId,
      definitionKey,
      Collections.singletonList(null)
    );
    return createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  public String createAndStoreProcessReport(String definitionKey) {
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto = createSingleProcessReportDefinitionDto(
      null,
      definitionKey,
      Collections.singletonList(null)
    );
    return createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  public String createAndStoreDecisionReport(String definitionKey) {
    SingleDecisionReportDefinitionRequestDto singleDecisionReportDefinitionDto =
      createSingleDecisionReportDefinitionDto(
        null,
        definitionKey,
        Collections.singletonList(null)
      );
    return createSingleDecisionReport(singleDecisionReportDefinitionDto);
  }

  public SingleProcessReportDefinitionRequestDto createSingleProcessReportDefinitionDto(final String collectionId,
                                                                                        final List<String> tenants) {
    return createSingleProcessReportDefinitionDto(collectionId, Collections.emptySet(), tenants);
  }

  public SingleProcessReportDefinitionRequestDto createSingleProcessReportDefinitionDto(final String collectionId,
                                                                                        final String definitionKey,
                                                                                        final List<String> tenants) {
    return createSingleProcessReportDefinitionDto(collectionId, Collections.singleton(definitionKey), tenants);
  }

  public SingleProcessReportDefinitionRequestDto createSingleProcessReportDefinitionDto(final String collectionId,
                                                                                        final Set<String> definitionKeys,
                                                                                        final List<String> tenants) {
    ProcessReportDataDto numberReport = TemplatedProcessReportDataBuilder
      .createReportData()
      .definitions(
        definitionKeys.stream().map(key -> new ReportDataDefinitionDto(key, tenants)).collect(Collectors.toList())
      )
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setData(numberReport);
    singleProcessReportDefinitionDto.setId(RANDOM_STRING);
    singleProcessReportDefinitionDto.setLastModifier(RANDOM_STRING);
    singleProcessReportDefinitionDto.setName(RANDOM_STRING);
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    singleProcessReportDefinitionDto.setCreated(someDate);
    singleProcessReportDefinitionDto.setLastModified(someDate);
    singleProcessReportDefinitionDto.setOwner(RANDOM_STRING);
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    return singleProcessReportDefinitionDto;
  }

  public SingleDecisionReportDefinitionRequestDto createSingleDecisionReportDefinitionDto(final String definitionKey) {
    return createSingleDecisionReportDefinitionDto(null, definitionKey, Collections.singletonList(null));
  }

  public Response createSingleProcessReportAsUserAndReturnResponse(String collectionId, String definitionKey,
                                                                   String username, String password) {
    return createSingleProcessReportAsUserRawResponse(createSingleProcessReportDefinitionDto(
      collectionId,
      definitionKey,
      Collections.singletonList(null)
    ), username, password);
  }

  public Response createSingleDecisionReportAsUser(String collectionId, String definitionKey, String username,
                                                   String password) {
    return createNewDecisionReportAsUserRawResponse(createSingleDecisionReportDefinitionDto(
      collectionId,
      definitionKey,
      Collections.singletonList(null)
    ), username, password);
  }

  public SingleDecisionReportDefinitionRequestDto createSingleDecisionReportDefinitionDto(final String collectionId,
                                                                                          final String definitionKey,
                                                                                          final List<String> tenants) {
    return createSingleDecisionReportDefinitionDto(collectionId, Collections.singleton(definitionKey), tenants);
  }

  public SingleDecisionReportDefinitionRequestDto createSingleDecisionReportDefinitionDto(final String collectionId,
                                                                                          final Set<String> definitionKeys,
                                                                                          final List<String> tenants) {
    DecisionReportDataDto rawDataReport = DecisionReportDataBuilder
      .create()
      .definitions(
        definitionKeys.stream().map(key -> new ReportDataDefinitionDto(key, tenants)).collect(Collectors.toList())
      )
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
      .build();
    SingleDecisionReportDefinitionRequestDto decisionReportDefinition = new SingleDecisionReportDefinitionRequestDto();
    decisionReportDefinition.setData(rawDataReport);
    decisionReportDefinition.setId(RANDOM_STRING);
    decisionReportDefinition.setLastModifier(RANDOM_STRING);
    decisionReportDefinition.setName(RANDOM_STRING);
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    decisionReportDefinition.setCreated(someDate);
    decisionReportDefinition.setLastModified(someDate);
    decisionReportDefinition.setOwner(RANDOM_STRING);
    decisionReportDefinition.setCollectionId(collectionId);
    return decisionReportDefinition;
  }

  public String createSingleProcessReport(SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto) {
    return getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  public Response createSingleProcessReportAndReturnResponse(SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto) {
    return getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute();
  }

  public String createSingleProcessReport(final ProcessReportDataDto data) {
    return createSingleProcessReport(data, null);
  }

  public String createSingleProcessReport(final ProcessReportDataDto data, final String collectionId) {
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setName(TEST_REPORT_NAME);
    singleProcessReportDefinitionDto.setData(data);
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    return createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  public String createSingleDecisionReport(DecisionReportDataDto data) {
    return createSingleDecisionReport(data, null);
  }

  public String createSingleDecisionReport(final DecisionReportDataDto data, final String collectionId) {
    SingleDecisionReportDefinitionRequestDto definitionDto = new SingleDecisionReportDefinitionRequestDto();
    definitionDto.setName(TEST_REPORT_NAME);
    definitionDto.setData(data);
    definitionDto.setCollectionId(collectionId);
    return getRequestExecutor()
      .buildCreateSingleDecisionReportRequest(definitionDto)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  public String createEmptySingleProcessReport() {
    return createEmptySingleProcessReportInCollection(null);
  }

  public String createEmptySingleProcessReportInCollection(final String collectionId) {
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    return createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  public String createEmptySingleDecisionReport() {
    return createEmptySingleDecisionReportInCollection(null);
  }

  public String createEmptySingleDecisionReportInCollection(final String collectionId) {
    SingleDecisionReportDefinitionRequestDto singleDecisionReportDefinitionDto =
      new SingleDecisionReportDefinitionRequestDto();
    singleDecisionReportDefinitionDto.setCollectionId(collectionId);
    return createSingleDecisionReport(singleDecisionReportDefinitionDto);
  }

  public String createReportForCollectionAsUser(final String collectionId, final DefinitionType resourceType,
                                                final String definitionKey, final List<String> tenants) {
    return createReportForCollectionAsUser(
      collectionId,
      resourceType,
      definitionKey,
      tenants,
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD
    );
  }

  public String createSingleProcessReportAsUser(final String definitionKey, final String user, final String pw) {
    return createReportForCollectionAsUser(
      null,
      DefinitionType.PROCESS,
      definitionKey,
      Collections.singletonList(null),
      user,
      pw
    );
  }

  public AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateReport(final String reportId) {
    return evaluateReportAsUser(reportId, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateReportAsKermit(final String reportId) {
    return evaluateReportAsUser(reportId, KERMIT_USER, KERMIT_USER);
  }

  public AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateReportAsUser(final String reportId,
                                                                                                    final String username,
                                                                                                    final String password) {
    return getRequestExecutor()
      .withUserAuthentication(username, password)
      .buildEvaluateSavedReportRequest(reportId)
      // @formatter:off
      .execute(new TypeReference<>() {});
    // @formatter:on
  }

  private String createReportForCollectionAsUser(final String collectionId, final DefinitionType resourceType,
                                                 final String definitionKey, final List<String> tenants,
                                                 final String user, final String pw) {
    switch (resourceType) {
      case PROCESS:
        SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
          createSingleProcessReportDefinitionDto(
            collectionId,
            definitionKey,
            tenants
          );
        return createSingleProcessReportAsUser(singleProcessReportDefinitionDto, user, pw);

      case DECISION:
        SingleDecisionReportDefinitionRequestDto singleDecisionReportDefinitionDto =
          createSingleDecisionReportDefinitionDto(
            collectionId,
            definitionKey,
            tenants
          );
        return createNewDecisionReportAsUser(singleDecisionReportDefinitionDto, user, pw);

      default:
        throw new OptimizeRuntimeException("Unknown definition type provided.");
    }
  }

  public Response createSingleProcessReportAsUserRawResponse(final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto,
                                                             final String user, final String pw) {
    return getRequestExecutor()
      .withUserAuthentication(user, pw)
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute();
  }

  public String createSingleProcessReportAsUser(final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto,
                                                final String user, final String pw) {
    return getRequestExecutor()
      .withUserAuthentication(user, pw)
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  private Response createNewDecisionReportAsUserRawResponse(final SingleDecisionReportDefinitionRequestDto singleDecisionReportDefinitionDto,
                                                            final String user, final String pw) {
    return getRequestExecutor()
      .withUserAuthentication(user, pw)
      .buildCreateSingleDecisionReportRequest(singleDecisionReportDefinitionDto)
      .execute();
  }


  public String createNewDecisionReportAsUser(final SingleDecisionReportDefinitionRequestDto singleDecisionReportDefinitionDto,
                                              final String user, final String pw) {
    return getRequestExecutor()
      .withUserAuthentication(user, pw)
      .buildCreateSingleDecisionReportRequest(singleDecisionReportDefinitionDto)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  public String createSingleDecisionReport(SingleDecisionReportDefinitionRequestDto decisionReportDefinition) {
    return getRequestExecutor()
      .buildCreateSingleDecisionReportRequest(decisionReportDefinition)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  public String createAndStoreDecisionReport(String collectionId, String definitionKey, List<String> tenants) {
    SingleDecisionReportDefinitionRequestDto decisionReportDefinition = createSingleDecisionReportDefinitionDto(
      collectionId,
      definitionKey,
      tenants
    );
    return createSingleDecisionReport(decisionReportDefinition);
  }

  public SingleProcessReportDefinitionRequestDto getSingleProcessReportDefinitionDto(String reportId) {
    return getSingleProcessReportDefinitionDto(reportId, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  private SingleProcessReportDefinitionRequestDto getSingleProcessReportDefinitionDto(String reportId, String username,
                                                                                      String password) {
    Response response = getSingleReportRawResponse(reportId, username, password);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    return response.readEntity(SingleProcessReportDefinitionRequestDto.class);
  }

  public Response getSingleReportRawResponse(String reportId, String username,
                                             String password) {
    return getRequestExecutor()
      .buildGetReportRequest(reportId)
      .withUserAuthentication(username, password)
      .execute();
  }

  public Response copyReportToCollection(String reportId, String collectionId) {
    return copyReportToCollection(reportId, collectionId, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public Response copyReportToCollection(String reportId, String collectionId, String username, String password) {
    return getRequestExecutor()
      .buildCopyReportRequest(reportId, collectionId)
      .withUserAuthentication(username, password)
      .execute();
  }

  public CombinedReportDefinitionRequestDto getCombinedProcessReportById(String reportId) {
    return getRequestExecutor()
      .buildGetReportRequest(reportId)
      .execute(CombinedReportDefinitionRequestDto.class, Response.Status.OK.getStatusCode());
  }

  public List<AuthorizedReportDefinitionResponseDto> getAllReportsAsUser() {
    return getAllReportsAsUser(DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public List<AuthorizedReportDefinitionResponseDto> getAllReportsAsUser(String username, String password) {
    return getRequestExecutor()
      .withUserAuthentication(username, password)
      .buildGetAllPrivateReportsRequest()
      .executeAndReturnList(AuthorizedReportDefinitionResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public SingleProcessReportDefinitionRequestDto getSingleProcessReportById(final String id) {
    return getRequestExecutor()
      .buildGetReportRequest(id)
      .execute(SingleProcessReportDefinitionRequestDto.class, Response.Status.OK.getStatusCode());
  }

  public SingleDecisionReportDefinitionRequestDto getSingleDecisionReportById(final String id) {
    return getRequestExecutor()
      .buildGetReportRequest(id)
      .execute(SingleDecisionReportDefinitionRequestDto.class, Response.Status.OK.getStatusCode());
  }

  public ReportDefinitionDto<?> getReportById(String id) {
    return getRequestExecutor()
      .buildGetReportRequest(id)
      .execute(ReportDefinitionDto.class, Response.Status.OK.getStatusCode());
  }

  public Response getReportByIdAsUserRawResponse(final String id, final String username, final String password) {
    return getRequestExecutor()
      .withUserAuthentication(username, password)
      .buildGetReportRequest(id)
      .execute();
  }

  public void deleteReport(final String reportId) {
    deleteReport(reportId, false);
  }

  public Response deleteReport(final String reportId, final boolean force, String username, String password) {
    return getRequestExecutor()
      .buildDeleteReportRequest(reportId, force)
      .withUserAuthentication(username, password)
      .execute();
  }

  public Response evaluateReportAsUserRawResponse(String id, String username, String password) {
    return getRequestExecutor()
      .buildEvaluateSavedReportRequest(id)
      .withUserAuthentication(username, password)
      .execute();
  }

  public Response deleteReport(final String reportId, final boolean force) {
    return deleteReport(reportId, force, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public void assertReportIsDeleted(final String singleReportIdToDelete) {
    getRequestExecutor()
      .buildGetReportRequest(singleReportIdToDelete)
      .execute(Response.Status.NOT_FOUND.getStatusCode());
  }

  public void updateSingleProcessReport(String reportId, SingleProcessReportDefinitionRequestDto report,
                                        boolean force) {
    updateSingleProcessReport(reportId, report, force, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public ConflictResponseDto getReportDeleteConflicts(String id) {
    return getRequestExecutor()
      .buildGetReportDeleteConflictsRequest(id)
      .execute(ConflictResponseDto.class, Response.Status.OK.getStatusCode());
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }

  public AuthorizedDecisionReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateMapReport(DecisionReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedDecisionReportEvaluationResponseDto<List<MapResultEntryDto>>>() {});
      // @formatter:on
  }

  public AuthorizedDecisionReportEvaluationResponseDto<Double> evaluateNumberReport(DecisionReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedDecisionReportEvaluationResponseDto<Double>>() {});
      // @formatter:on
  }

  public AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>> evaluateDecisionRawReport(
    DecisionReportDataDto reportData,
    PaginationRequestDto paginationDto) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequestWithPagination(reportData, paginationDto)
      // @formatter:off
      .execute(new TypeReference<AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>>>() {});
      // @formatter:on
  }

  public AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> evaluateRawReport(
    ProcessReportDataDto reportData,
    PaginationRequestDto paginationDto) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequestWithPagination(reportData, paginationDto)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>>>() {});
      // @formatter:on
  }

  public AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>> evaluateDecisionRawReport(
    final DecisionReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>>>() {});
      // @formatter:on
  }

  public AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>> evaluateDecisionRawReportById(
    final String id) {
    return getRequestExecutor()
      .buildEvaluateSavedReportRequest(id)
      // @formatter:off
      .execute(new TypeReference<>() {});
    // @formatter:on
  }

  public Response evaluateReportAndReturnResponse(DecisionReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }

  public AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluateHyperMapReportById(String id) {
    return getRequestExecutor()
      .buildEvaluateSavedReportRequest(id)
      // @formatter:off
      .execute(new TypeReference<>() {});
      // @formatter:on
  }

  public AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateMapReportById(String id) {
    return getRequestExecutor()
      .buildEvaluateSavedReportRequest(id)
      // @formatter:off
      .execute(new TypeReference<>() {});
      // @formatter:on
  }

  public AuthorizedProcessReportEvaluationResponseDto<Double> evaluateNumberReportById(String id) {
    return getRequestExecutor()
      .buildEvaluateSavedReportRequest(id)
      // @formatter:off
      .execute(new TypeReference<>() {});
      // @formatter:on
  }

  public AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> evaluateRawReportById(final String reportId) {
    return getRequestExecutor()
      .buildEvaluateSavedReportRequest(reportId)
      // @formatter:off
      .execute(new TypeReference<>() {});
      // @formatter:on
  }

  public AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateMapReport(ProcessReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<>() {});
      // @formatter:on
  }

  public AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateMapReport(
    final String reportId,
    final AdditionalProcessReportEvaluationFilterDto filters) {
    return getRequestExecutor()
      .buildEvaluateSavedReportRequest(reportId, filters)
      // @formatter:off
      .execute(new TypeReference<>() {});
    // @formatter:on
  }

  public AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluateHyperMapReport(ProcessReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<>() {});
      // @formatter:on
  }

  public AuthorizedProcessReportEvaluationResponseDto<Double> evaluateNumberReport(ProcessReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<>() {});
      // @formatter:on
  }

  public AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> evaluateRawReport(ProcessReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<>() {});
      // @formatter:on
  }

  public Response evaluateReportAndReturnResponse(ProcessReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }

  public Response evaluateReportAndReturnResponse(SingleReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }

  public Response evaluateReportAsUserAndReturnResponse(SingleReportDataDto reportData, String username,
                                                        String password) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .withUserAuthentication(username, password)
      .execute();
  }

  public <T> AuthorizedCombinedReportEvaluationResponseDto<T> evaluateCombinedReportById(String reportId) {
    return evaluateCombinedReportByIdWithAdditionalFilters(reportId, null);
  }

  public <T> AuthorizedCombinedReportEvaluationResponseDto<T> evaluateCombinedReportByIdWithAdditionalFilters(
    final String reportId,
    final AdditionalProcessReportEvaluationFilterDto filters) {
    return getRequestExecutor()
      .buildEvaluateSavedReportRequest(reportId, filters)
      // @formatter:off
      .execute(new TypeReference<>() {});
      // @formatter:on
  }

  public <T> CombinedProcessReportResultDataDto<T> evaluateUnsavedCombined(CombinedReportDataDto reportDataDto) {
    return getRequestExecutor()
      .buildEvaluateCombinedUnsavedReportRequest(reportDataDto)
      // @formatter:off
      .execute(new TypeReference<AuthorizedCombinedReportEvaluationResponseDto<T>>() {})
      // @formatter:on
      .getResult();
  }

  public <T> CombinedProcessReportResultDataDto<T> saveAndEvaluateCombinedReport(
    final List<String> reportIds) {
    final List<CombinedReportItemDto> reportItems = reportIds.stream()
      .map(CombinedReportItemDto::new)
      .collect(toList());

    final CombinedReportDataDto combinedReportData = new CombinedReportDataDto();
    combinedReportData.setReports(reportItems);
    final CombinedReportDefinitionRequestDto combinedReport = new CombinedReportDefinitionRequestDto();
    combinedReport.setData(combinedReportData);

    final IdResponseDto response = getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReport)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode());

    final AuthorizedCombinedReportEvaluationResponseDto<T> evaluationResultDto =
      evaluateCombinedReportById(response.getId());
    return evaluationResultDto.getResult();
  }

  public <T> AuthorizedSingleReportEvaluationResponseDto<T, SingleProcessReportDefinitionRequestDto> evaluateReport(
    final ProcessReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<>() {});
      // @formatter:on
  }

  public <T> AuthorizedSingleReportEvaluationResponseDto<T, SingleDecisionReportDefinitionRequestDto> evaluateReport(
    final DecisionReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<>() {});
      // @formatter:off
  }

  public List<MapResultEntryDto> evaluateReportAndReturnMapResult(final ProcessReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>>() {})
      // @formatter:on
      .getResult()
      .getFirstMeasureData();
  }

  public <T, DD extends SingleReportDefinitionDto<?>> AuthorizedProcessReportEvaluationResponseDto<T> evaluateProcessReport(final DD reportDefinition) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportDefinition)
      // @formatter:off
      .execute(new TypeReference<>() {});
      // @formatter:on
  }

  public <T> AuthorizedProcessReportEvaluationResponseDto<T> evaluateProcessReportLocalized(final String reportId,
                                                                                            final String locale) {
    final OptimizeRequestExecutor requestExecutor = getRequestExecutor();
    Optional.ofNullable(locale).ifPresent(loc -> requestExecutor.addSingleHeader(X_OPTIMIZE_CLIENT_LOCALE, loc));
    return requestExecutor
      .buildEvaluateSavedReportRequest(reportId)
      // @formatter:off
      .execute(new TypeReference<>() {});
      // @formatter:on
  }

  public <T, DD extends SingleReportDefinitionDto<?>> AuthorizedDecisionReportEvaluationResponseDto<T> evaluateDecisionReport(final DD reportDefinition) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportDefinition)
      // @formatter:off
      .execute(new TypeReference<>() {});
      // @formatter:on
  }
}
