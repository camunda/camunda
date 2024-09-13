/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.optimize;

import static io.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
import static io.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static io.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_LOCALE;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.optimize.OptimizeRequestExecutor;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import io.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationRequestDto;
import io.camunda.optimize.dto.optimize.rest.report.AuthorizedDecisionReportEvaluationResponseDto;
import io.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import io.camunda.optimize.dto.optimize.rest.report.AuthorizedSingleReportEvaluationResponseDto;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.ProcessReportDataType;
import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import io.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import io.camunda.optimize.test.util.decision.DecisionReportDataType;
import jakarta.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ReportClient {

  private static final String RANDOM_STRING = "something";
  private static final String TEST_REPORT_NAME = "My test report";

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public Response updateSingleProcessReport(
      final String reportId, final ReportDefinitionDto updatedReport) {
    return updateSingleProcessReport(
        reportId, updatedReport, false, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public Response updateSingleProcessReport(
      final String reportId,
      final ReportDefinitionDto updatedReport,
      final Boolean force,
      final String username,
      final String password) {
    return getRequestExecutor()
        .buildUpdateSingleProcessReportRequest(reportId, updatedReport, force)
        .withUserAuthentication(username, password)
        .execute();
  }

  public Response updateDecisionReport(
      final String reportId, final ReportDefinitionDto updatedReport) {
    return updateDecisionReport(reportId, updatedReport, false, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public Response updateDecisionReport(
      final String reportId,
      final ReportDefinitionDto updatedReport,
      final Boolean force,
      final String username,
      final String password) {
    return getRequestExecutor()
        .withUserAuthentication(username, password)
        .buildUpdateSingleDecisionReportRequest(reportId, updatedReport, force)
        .execute();
  }

  public String createSingleReport(
      final String collectionId,
      final DefinitionType definitionType,
      final String definitionKey,
      final List<String> tenants) {
    switch (definitionType) {
      case PROCESS:
        return createAndStoreProcessReport(collectionId, definitionKey, tenants);
      case DECISION:
        return createAndStoreDecisionReport(collectionId, definitionKey, tenants);
      default:
        throw new IllegalStateException("Uncovered definitionType: " + definitionType);
    }
  }

  public String createAndStoreProcessReport(
      final String collectionId, final String definitionKey, final List<String> tenants) {
    final ProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
        createSingleProcessReportDefinitionDto(collectionId, definitionKey, tenants);
    return createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  public String createAndStoreProcessReport(
      final String definitionKey, final List<String> tenants) {
    final ProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
        createSingleProcessReportDefinitionDto(null, definitionKey, tenants);
    return createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  public String createAndStoreProcessReport(final String collectionId, final String definitionKey) {
    final ProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
        createSingleProcessReportDefinitionDto(
            collectionId, definitionKey, Collections.singletonList(null));
    return createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  public String createAndStoreProcessReport(final String definitionKey) {
    final ProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
        createSingleProcessReportDefinitionDto(
            null, definitionKey, Collections.singletonList(null));
    return createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  public String createAndStoreDecisionReport(final String definitionKey) {
    final DecisionReportDefinitionRequestDto singleDecisionReportDefinitionDto =
        createSingleDecisionReportDefinitionDto(
            null, definitionKey, Collections.singletonList(null));
    return createSingleDecisionReport(singleDecisionReportDefinitionDto);
  }

  public ProcessReportDefinitionRequestDto createSingleProcessReportDefinitionDto(
      final String collectionId, final List<String> tenants) {
    return createSingleProcessReportDefinitionDto(collectionId, Collections.emptySet(), tenants);
  }

  public ProcessReportDefinitionRequestDto createSingleProcessReportDefinitionDto(
      final String collectionId, final String definitionKey, final List<String> tenants) {
    return createSingleProcessReportDefinitionDto(
        collectionId, Collections.singleton(definitionKey), tenants);
  }

  public ProcessReportDefinitionRequestDto createSingleProcessReportDefinitionDto(
      final String collectionId, final Set<String> definitionKeys, final List<String> tenants) {
    final ProcessReportDataDto numberReport =
        TemplatedProcessReportDataBuilder.createReportData()
            .definitions(
                definitionKeys.stream()
                    .map(key -> new ReportDataDefinitionDto(key, tenants))
                    .collect(Collectors.toList()))
            .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE)
            .build();
    final ProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
        new ProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setData(numberReport);
    singleProcessReportDefinitionDto.setId(RANDOM_STRING);
    singleProcessReportDefinitionDto.setLastModifier(RANDOM_STRING);
    singleProcessReportDefinitionDto.setName(RANDOM_STRING);
    final OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    singleProcessReportDefinitionDto.setCreated(someDate);
    singleProcessReportDefinitionDto.setLastModified(someDate);
    singleProcessReportDefinitionDto.setOwner(RANDOM_STRING);
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    return singleProcessReportDefinitionDto;
  }

  public DecisionReportDefinitionRequestDto createSingleDecisionReportDefinitionDto(
      final String definitionKey) {
    return createSingleDecisionReportDefinitionDto(
        null, definitionKey, Collections.singletonList(null));
  }

  public Response createSingleProcessReportAsUserAndReturnResponse(
      final String collectionId,
      final String definitionKey,
      final String username,
      final String password) {
    return createSingleProcessReportAsUserRawResponse(
        createSingleProcessReportDefinitionDto(
            collectionId, definitionKey, Collections.singletonList(null)),
        username,
        password);
  }

  public Response createSingleDecisionReportAsUser(
      final String collectionId,
      final String definitionKey,
      final String username,
      final String password) {
    return createNewDecisionReportAsUserRawResponse(
        createSingleDecisionReportDefinitionDto(
            collectionId, definitionKey, Collections.singletonList(null)),
        username,
        password);
  }

  public DecisionReportDefinitionRequestDto createSingleDecisionReportDefinitionDto(
      final String collectionId, final String definitionKey, final List<String> tenants) {
    return createSingleDecisionReportDefinitionDto(
        collectionId, Collections.singleton(definitionKey), tenants);
  }

  public DecisionReportDefinitionRequestDto createSingleDecisionReportDefinitionDto(
      final String collectionId, final Set<String> definitionKeys, final List<String> tenants) {
    final DecisionReportDataDto rawDataReport =
        DecisionReportDataBuilder.create()
            .definitions(
                definitionKeys.stream()
                    .map(key -> new ReportDataDefinitionDto(key, tenants))
                    .collect(Collectors.toList()))
            .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
            .build();
    final DecisionReportDefinitionRequestDto decisionReportDefinition =
        new DecisionReportDefinitionRequestDto();
    decisionReportDefinition.setData(rawDataReport);
    decisionReportDefinition.setId(RANDOM_STRING);
    decisionReportDefinition.setLastModifier(RANDOM_STRING);
    decisionReportDefinition.setName(RANDOM_STRING);
    final OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    decisionReportDefinition.setCreated(someDate);
    decisionReportDefinition.setLastModified(someDate);
    decisionReportDefinition.setOwner(RANDOM_STRING);
    decisionReportDefinition.setCollectionId(collectionId);
    return decisionReportDefinition;
  }

  public String createSingleProcessReport(
      final ProcessReportDefinitionRequestDto singleProcessReportDefinitionDto) {
    return getRequestExecutor()
        .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
        .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
        .getId();
  }

  public Response createSingleProcessReportAndReturnResponse(
      final ProcessReportDefinitionRequestDto singleProcessReportDefinitionDto) {
    return getRequestExecutor()
        .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
        .execute();
  }

  public String createSingleProcessReport(final ProcessReportDataDto data) {
    return createSingleProcessReport(data, null);
  }

  public String createSingleProcessReport(
      final ProcessReportDataDto data, final String collectionId) {
    final ProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
        new ProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setName(TEST_REPORT_NAME);
    singleProcessReportDefinitionDto.setData(data);
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    return createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  public String createSingleDecisionReport(final DecisionReportDataDto data) {
    return createSingleDecisionReport(data, null);
  }

  public String createSingleDecisionReport(
      final DecisionReportDataDto data, final String collectionId) {
    final DecisionReportDefinitionRequestDto definitionDto =
        new DecisionReportDefinitionRequestDto();
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
    final ProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
        new ProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    return createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  public String createEmptySingleDecisionReport() {
    return createEmptySingleDecisionReportInCollection(null);
  }

  public String createEmptySingleDecisionReportInCollection(final String collectionId) {
    final DecisionReportDefinitionRequestDto singleDecisionReportDefinitionDto =
        new DecisionReportDefinitionRequestDto();
    singleDecisionReportDefinitionDto.setCollectionId(collectionId);
    return createSingleDecisionReport(singleDecisionReportDefinitionDto);
  }

  public String createReportForCollectionAsUser(
      final String collectionId,
      final DefinitionType resourceType,
      final String definitionKey,
      final List<String> tenants) {
    return createReportForCollectionAsUser(
        collectionId, resourceType, definitionKey, tenants, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public String createSingleProcessReportAsUser(
      final String definitionKey, final String user, final String pw) {
    return createReportForCollectionAsUser(
        null, DefinitionType.PROCESS, definitionKey, Collections.singletonList(null), user, pw);
  }

  public AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateReport(
      final String reportId) {
    return evaluateReportAsUser(reportId, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>
      evaluateReportAsKermit(final String reportId) {
    return evaluateReportAsUser(reportId, "kermit", "kermit");
  }

  public AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateReportAsUser(
      final String reportId, final String username, final String password) {
    return getRequestExecutor()
        .withUserAuthentication(username, password)
        .buildEvaluateSavedReportRequest(reportId)
        // @formatter:off
        .execute(new TypeReference<>() {});
    // @formatter:on
  }

  private String createReportForCollectionAsUser(
      final String collectionId,
      final DefinitionType resourceType,
      final String definitionKey,
      final List<String> tenants,
      final String user,
      final String pw) {
    switch (resourceType) {
      case PROCESS:
        final ProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
            createSingleProcessReportDefinitionDto(collectionId, definitionKey, tenants);
        return createSingleProcessReportAsUser(singleProcessReportDefinitionDto, user, pw);

      case DECISION:
        final DecisionReportDefinitionRequestDto singleDecisionReportDefinitionDto =
            createSingleDecisionReportDefinitionDto(collectionId, definitionKey, tenants);
        return createNewDecisionReportAsUser(singleDecisionReportDefinitionDto, user, pw);

      default:
        throw new OptimizeRuntimeException("Unknown definition type provided.");
    }
  }

  public Response createSingleProcessReportAsUserRawResponse(
      final ProcessReportDefinitionRequestDto singleProcessReportDefinitionDto,
      final String user,
      final String pw) {
    return getRequestExecutor()
        .withUserAuthentication(user, pw)
        .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
        .execute();
  }

  public String createSingleProcessReportAsUser(
      final ProcessReportDefinitionRequestDto singleProcessReportDefinitionDto,
      final String user,
      final String pw) {
    return getRequestExecutor()
        .withUserAuthentication(user, pw)
        .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
        .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
        .getId();
  }

  private Response createNewDecisionReportAsUserRawResponse(
      final DecisionReportDefinitionRequestDto singleDecisionReportDefinitionDto,
      final String user,
      final String pw) {
    return getRequestExecutor()
        .withUserAuthentication(user, pw)
        .buildCreateSingleDecisionReportRequest(singleDecisionReportDefinitionDto)
        .execute();
  }

  public String createNewDecisionReportAsUser(
      final DecisionReportDefinitionRequestDto singleDecisionReportDefinitionDto,
      final String user,
      final String pw) {
    return getRequestExecutor()
        .withUserAuthentication(user, pw)
        .buildCreateSingleDecisionReportRequest(singleDecisionReportDefinitionDto)
        .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
        .getId();
  }

  public String createSingleDecisionReport(
      final DecisionReportDefinitionRequestDto decisionReportDefinition) {
    return getRequestExecutor()
        .buildCreateSingleDecisionReportRequest(decisionReportDefinition)
        .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
        .getId();
  }

  public String createAndStoreDecisionReport(
      final String collectionId, final String definitionKey, final List<String> tenants) {
    final DecisionReportDefinitionRequestDto decisionReportDefinition =
        createSingleDecisionReportDefinitionDto(collectionId, definitionKey, tenants);
    return createSingleDecisionReport(decisionReportDefinition);
  }

  public ProcessReportDefinitionRequestDto getSingleProcessReportDefinitionDto(
      final String reportId) {
    return getSingleProcessReportDefinitionDto(reportId, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  private ProcessReportDefinitionRequestDto getSingleProcessReportDefinitionDto(
      final String reportId, final String username, final String password) {
    final Response response = getSingleReportRawResponse(reportId, username, password);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    return response.readEntity(ProcessReportDefinitionRequestDto.class);
  }

  public Response getSingleReportRawResponse(
      final String reportId, final String username, final String password) {
    return getRequestExecutor()
        .buildGetReportRequest(reportId)
        .withUserAuthentication(username, password)
        .execute();
  }

  public Response copyReportToCollection(final String reportId, final String collectionId) {
    return copyReportToCollection(reportId, collectionId, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public Response copyReportToCollection(
      final String reportId,
      final String collectionId,
      final String username,
      final String password) {
    return getRequestExecutor()
        .buildCopyReportRequest(reportId, collectionId)
        .withUserAuthentication(username, password)
        .execute();
  }

  public List<AuthorizedReportDefinitionResponseDto> getAllReportsAsUser() {
    return getAllReportsAsUser(DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public List<AuthorizedReportDefinitionResponseDto> getAllReportsAsUser(
      final String username, final String password) {
    return getRequestExecutor()
        .withUserAuthentication(username, password)
        .buildGetAllPrivateReportsRequest()
        .executeAndReturnList(
            AuthorizedReportDefinitionResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public ProcessReportDefinitionRequestDto getSingleProcessReportById(final String id) {
    return getRequestExecutor()
        .buildGetReportRequest(id)
        .execute(ProcessReportDefinitionRequestDto.class, Response.Status.OK.getStatusCode());
  }

  public DecisionReportDefinitionRequestDto getSingleDecisionReportById(final String id) {
    return getRequestExecutor()
        .buildGetReportRequest(id)
        .execute(
            DecisionReportDefinitionRequestDto.class, Response.Status.OK.getStatusCode());
  }

  public ReportDefinitionDto<?> getReportById(final String id) {
    return getRequestExecutor()
        .buildGetReportRequest(id)
        .execute(ReportDefinitionDto.class, Response.Status.OK.getStatusCode());
  }

  public ReportDefinitionDto<?> getReportById(final String id, final String locale) {
    final OptimizeRequestExecutor requestExecutor = getRequestExecutor();
    Optional.ofNullable(locale)
        .ifPresent(loc -> requestExecutor.addSingleHeader(X_OPTIMIZE_CLIENT_LOCALE, locale));
    return requestExecutor
        .buildGetReportRequest(id)
        .execute(ReportDefinitionDto.class, Response.Status.OK.getStatusCode());
  }

  public Response getReportByIdAsUserRawResponse(
      final String id, final String username, final String password) {
    return getRequestExecutor()
        .withUserAuthentication(username, password)
        .buildGetReportRequest(id)
        .execute();
  }

  public void deleteReport(final String reportId) {
    deleteReport(reportId, false);
  }

  public Response deleteReport(
      final String reportId, final boolean force, final String username, final String password) {
    return getRequestExecutor()
        .buildDeleteReportRequest(reportId, force)
        .withUserAuthentication(username, password)
        .execute();
  }

  public Response evaluateReportAsUserRawResponse(
      final String id, final String username, final String password) {
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

  public void updateSingleProcessReport(
      final String reportId,
      final ProcessReportDefinitionRequestDto report,
      final boolean force) {
    updateSingleProcessReport(reportId, report, force, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public ConflictResponseDto getReportDeleteConflicts(final String id) {
    return getRequestExecutor()
        .buildGetReportDeleteConflictsRequest(id)
        .execute(ConflictResponseDto.class, Response.Status.OK.getStatusCode());
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }

  public AuthorizedDecisionReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateMapReport(
      final DecisionReportDataDto reportData) {
    return getRequestExecutor()
        .buildEvaluateSingleUnsavedReportRequest(reportData)
        // @formatter:off
        .execute(
            new TypeReference<
                AuthorizedDecisionReportEvaluationResponseDto<List<MapResultEntryDto>>>() {});
    // @formatter:on
  }

  public AuthorizedDecisionReportEvaluationResponseDto<Double> evaluateNumberReport(
      final DecisionReportDataDto reportData) {
    return getRequestExecutor()
        .buildEvaluateSingleUnsavedReportRequest(reportData)
        // @formatter:off
        .execute(new TypeReference<AuthorizedDecisionReportEvaluationResponseDto<Double>>() {});
    // @formatter:on
  }

  public AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>>
      evaluateDecisionRawReport(
          final DecisionReportDataDto reportData, final PaginationRequestDto paginationDto) {
    return getRequestExecutor()
        .buildEvaluateSingleUnsavedReportRequestWithPagination(reportData, paginationDto)
        // @formatter:off
        .execute(
            new TypeReference<
                AuthorizedDecisionReportEvaluationResponseDto<
                    List<RawDataDecisionInstanceDto>>>() {});
    // @formatter:on
  }

  public AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>>
      evaluateRawReport(
          final ProcessReportDataDto reportData, final PaginationRequestDto paginationDto) {
    return getRequestExecutor()
        .buildEvaluateSingleUnsavedReportRequestWithPagination(reportData, paginationDto)
        // @formatter:off
        .execute(
            new TypeReference<
                AuthorizedProcessReportEvaluationResponseDto<
                    List<RawDataProcessInstanceDto>>>() {});
    // @formatter:on
  }

  public AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>>
      evaluateDecisionRawReport(final DecisionReportDataDto reportData) {
    return getRequestExecutor()
        .buildEvaluateSingleUnsavedReportRequest(reportData)
        // @formatter:off
        .execute(
            new TypeReference<
                AuthorizedDecisionReportEvaluationResponseDto<
                    List<RawDataDecisionInstanceDto>>>() {});
    // @formatter:on
  }

  public AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>>
      evaluateDecisionRawReportById(final String id) {
    return getRequestExecutor()
        .buildEvaluateSavedReportRequest(id)
        // @formatter:off
        .execute(new TypeReference<>() {});
    // @formatter:on
  }

  public Response evaluateReportAndReturnResponse(final DecisionReportDataDto reportData) {
    return getRequestExecutor().buildEvaluateSingleUnsavedReportRequest(reportData).execute();
  }

  public AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>>
      evaluateHyperMapReportById(final String id) {
    return getRequestExecutor()
        .buildEvaluateSavedReportRequest(id)
        // @formatter:off
        .execute(new TypeReference<>() {});
    // @formatter:on
  }

  public AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>
      evaluateMapReportById(final String id) {
    return getRequestExecutor()
        .buildEvaluateSavedReportRequest(id)
        // @formatter:off
        .execute(new TypeReference<>() {});
    // @formatter:on
  }

  public AuthorizedProcessReportEvaluationResponseDto<Double> evaluateNumberReportById(
      final String id) {
    return getRequestExecutor()
        .buildEvaluateSavedReportRequest(id)
        // @formatter:off
        .execute(new TypeReference<>() {});
    // @formatter:on
  }

  public AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>>
      evaluateRawReportById(final String reportId) {
    return getRequestExecutor()
        .buildEvaluateSavedReportRequest(reportId)
        // @formatter:off
        .execute(new TypeReference<>() {});
    // @formatter:on
  }

  public AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateMapReport(
      final ProcessReportDataDto reportData) {
    return getRequestExecutor()
        .buildEvaluateSingleUnsavedReportRequest(reportData)
        // @formatter:off
        .execute(new TypeReference<>() {});
    // @formatter:on
  }

  public AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateMapReport(
      final String reportId, final AdditionalProcessReportEvaluationFilterDto filters) {
    return getRequestExecutor()
        .buildEvaluateSavedReportRequest(reportId, filters)
        // @formatter:off
        .execute(new TypeReference<>() {});
    // @formatter:on
  }

  public AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>>
      evaluateHyperMapReport(final ProcessReportDataDto reportData) {
    return getRequestExecutor()
        .buildEvaluateSingleUnsavedReportRequest(reportData)
        // @formatter:off
        .execute(new TypeReference<>() {});
    // @formatter:on
  }

  public AuthorizedProcessReportEvaluationResponseDto<Double> evaluateNumberReport(
      final ProcessReportDataDto reportData) {
    return getRequestExecutor()
        .buildEvaluateSingleUnsavedReportRequest(reportData)
        // @formatter:off
        .execute(new TypeReference<>() {});
    // @formatter:on
  }

  public AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>>
      evaluateRawReport(final ProcessReportDataDto reportData) {
    return getRequestExecutor()
        .buildEvaluateSingleUnsavedReportRequest(reportData)
        // @formatter:off
        .execute(new TypeReference<>() {});
    // @formatter:on
  }

  public Response evaluateReportAndReturnResponse(final ProcessReportDataDto reportData) {
    return getRequestExecutor().buildEvaluateSingleUnsavedReportRequest(reportData).execute();
  }

  public Response evaluateReportAndReturnResponse(final ReportDataDto reportData) {
    return getRequestExecutor().buildEvaluateSingleUnsavedReportRequest(reportData).execute();
  }

  public <T>
      AuthorizedSingleReportEvaluationResponseDto<T, ProcessReportDefinitionRequestDto>
          evaluateReport(final ProcessReportDataDto reportData) {
    return getRequestExecutor()
        .buildEvaluateSingleUnsavedReportRequest(reportData)
        // @formatter:off
        .execute(new TypeReference<>() {});
    // @formatter:on
  }

  public <T>
      AuthorizedSingleReportEvaluationResponseDto<T, DecisionReportDefinitionRequestDto>
          evaluateReport(final DecisionReportDataDto reportData) {
    return getRequestExecutor()
        .buildEvaluateSingleUnsavedReportRequest(reportData)
        // @formatter:off
        .execute(new TypeReference<>() {});
    // @formatter:off
  }

  public List<MapResultEntryDto> evaluateReportAndReturnMapResult(
      final ProcessReportDataDto reportData) {
    return getRequestExecutor()
        .buildEvaluateSingleUnsavedReportRequest(reportData)
        // @formatter:off
        .execute(
            new TypeReference<
                AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>>() {})
        // @formatter:on
        .getResult()
        .getFirstMeasureData();
  }

  public <T, DD extends ReportDefinitionDto<?>>
      AuthorizedProcessReportEvaluationResponseDto<T> evaluateProcessReport(
          final DD reportDefinition) {
    return getRequestExecutor()
        .buildEvaluateSingleUnsavedReportRequest(reportDefinition)
        // @formatter:off
        .execute(new TypeReference<>() {});
    // @formatter:on
  }

  public <T> AuthorizedProcessReportEvaluationResponseDto<T> evaluateProcessReportLocalized(
      final String reportId, final String locale) {
    final OptimizeRequestExecutor requestExecutor = getRequestExecutor();
    Optional.ofNullable(locale)
        .ifPresent(loc -> requestExecutor.addSingleHeader(X_OPTIMIZE_CLIENT_LOCALE, loc));
    return requestExecutor
        .buildEvaluateSavedReportRequest(reportId)
        // @formatter:off
        .execute(new TypeReference<>() {});
    // @formatter:on
  }

  public <T, DD extends ReportDefinitionDto<?>>
      AuthorizedDecisionReportEvaluationResponseDto<T> evaluateDecisionReport(
          final DD reportDefinition) {
    return getRequestExecutor()
        .buildEvaluateSingleUnsavedReportRequest(reportDefinition)
        // @formatter:off
        .execute(new TypeReference<>() {});
    // @formatter:on
  }
}
