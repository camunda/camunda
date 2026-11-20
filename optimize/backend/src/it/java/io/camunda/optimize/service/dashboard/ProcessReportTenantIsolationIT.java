/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import static io.camunda.optimize.AgenticInstanceFixtures.agenticInstanceWithTokens;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_COMPLETED_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticReportFilters.noExtraFilters;
import static io.camunda.optimize.service.dashboard.AgenticReportFilters.withDefinitions;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.COUNT_BY_PROCESS_REPORT_ID;
import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_DATA_SOURCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.dto.optimize.query.report.single.result.MeasureDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import io.camunda.optimize.rest.exceptions.ForbiddenException;
import io.camunda.optimize.service.db.report.result.NumberCommandResult;
import io.camunda.optimize.service.report.ReportEvaluationService;
import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.optimize.service.security.util.tenant.CamundaCCSMTenantAuthorizationService;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Guards camunda/camunda#70131 end to end: with multi-tenancy enabled, a process report must only
 * evaluate instances from the tenants the current user is authorized for. The definition list of a
 * report is what scopes its query to tenants, so every report type that can reach the query with an
 * empty definition list is covered: business value and agentic dashboard tiles, management reports
 * and instant preview reports. User-built reports cannot: they are rejected during validation when
 * no process is selected.
 *
 * <p>A selected or previewed process that exists, but only in tenants the user cannot see, is
 * rejected with a {@link ForbiddenException} before evaluation, so those cases assert the rejection
 * rather than an empty result.
 *
 * <p>Tenant authorizations come from Identity via {@link CCSMTokenService}. Instead of replacing
 * that bean with {@code @MockitoBean}, which would start a second Spring context that collides with
 * the shared fixed HTTP port (see {@code CslDefaultEnabledIT}), the token service is swapped inside
 * the shared {@link CamundaCCSMTenantAuthorizationService} for the duration of each test.
 */
class ProcessReportTenantIsolationIT extends AbstractBrokerlessZeebeCCSMIT {

  private static final String USER_ID = "testUser";
  private static final ZoneId UTC = ZoneId.of("UTC");
  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";
  private static final String TENANT_WITHOUT_DATA = "tenant-without-data";

  private AgenticReportEvaluator reports;
  private ReportEvaluationService reportEvaluationService;
  private CamundaCCSMTenantAuthorizationService tenantAuthorizationService;
  private Object originalTokenService;

  @BeforeEach
  void setUp() {
    embeddedOptimizeExtension.getBean(BusinessValueDashboardService.class).reconcile();
    embeddedOptimizeExtension.getBean(AgenticControlDashboardService.class).reconcile();
    reportEvaluationService = embeddedOptimizeExtension.getBean(ReportEvaluationService.class);
    reports = new AgenticReportEvaluator(reportEvaluationService);
    embeddedOptimizeExtension.getConfigurationService().setMultiTenancyEnabled(true);
    tenantAuthorizationService =
        embeddedOptimizeExtension.getBean(CamundaCCSMTenantAuthorizationService.class);
    originalTokenService =
        ReflectionTestUtils.getField(tenantAuthorizationService, "ccsmTokenService");
  }

  @AfterEach
  void restoreTenantAuthorization() {
    ReflectionTestUtils.setField(
        tenantAuthorizationService, "ccsmTokenService", originalTokenService);
    embeddedOptimizeExtension.getConfigurationService().setMultiTenancyEnabled(false);
  }

  // --- business value dashboard tile ---

  @Test
  void shouldReturnNoBusinessValueDataWhenUserTenantsHoldNoDefinitions() {
    // given data in tenant A, and a user only authorized for a tenant without any definitions
    persistInstancesInTenant(plainInstance(), uniqueKey(), TENANT_A, 3);
    authorizeCurrentUserFor(TENANT_WITHOUT_DATA);

    // when evaluating the tile without a process selected
    final List<MapResultEntryDto> result =
        reports.evaluateMapData(COUNT_BY_PROCESS_REPORT_ID, noExtraFilters());

    // then none of tenant A's instances are returned
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnNoBusinessValueDataWhenSelectedProcessIsUnknown() {
    // given data in tenant A, and a user authorized for it who selects an unknown process
    persistInstancesInTenant(plainInstance(), uniqueKey(), TENANT_A, 3);
    authorizeCurrentUserFor(TENANT_A);

    // when
    final List<MapResultEntryDto> result =
        reports.evaluateMapData(COUNT_BY_PROCESS_REPORT_ID, selecting(uniqueKey()));

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldRejectBusinessValueSelectionOfProcessOnlyInUnauthorizedTenant() {
    // given a process that only exists in tenant B, and a user only authorized for tenant A
    final String processKey = uniqueKey();
    persistInstancesInTenant(plainInstance(), processKey, TENANT_B, 3);
    authorizeCurrentUserFor(TENANT_A);

    // when the user selects that process
    // then
    assertThatThrownBy(
            () -> reports.evaluateMapData(COUNT_BY_PROCESS_REPORT_ID, selecting(processKey)))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void shouldOnlyReturnBusinessValueDataFromAuthorizedTenants() {
    // given a process deployed to both tenants, and a process that only exists in tenant B
    final String sharedKey = uniqueKey();
    persistInstancesInTenant(plainInstance(), sharedKey, TENANT_A, 2);
    persistInstancesInTenant(plainInstance(), sharedKey, TENANT_B, 3);
    persistInstancesInTenant(plainInstance(), uniqueKey(), TENANT_B, 4);
    authorizeCurrentUserFor(TENANT_A);

    // when evaluating the tile without a process selected
    final List<MapResultEntryDto> result =
        reports.evaluateMapData(COUNT_BY_PROCESS_REPORT_ID, noExtraFilters());

    // then only tenant A's instances are counted
    assertThat(result)
        .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
        .containsExactly(tuple(sharedKey, 2.0));
  }

  // --- agentic dashboard tile ---

  @Test
  void shouldReturnNoAgenticDataWhenUserTenantsHoldNoDefinitions() {
    // given agentic data in tenant A, and a user only authorized for a tenant without definitions
    persistInstancesInTenant(agenticInstance(), uniqueKey(), TENANT_A, 3);
    authorizeCurrentUserFor(TENANT_WITHOUT_DATA);

    // when evaluating the completed-runs tile without a process selected
    final Double result = reports.evaluateNumber(KPI_COMPLETED_REPORT_ID, noExtraFilters());

    // then none of tenant A's instances are counted
    assertThat(countOf(result)).isZero();
  }

  @Test
  void shouldReturnNoAgenticDataWhenSelectedProcessIsUnknown() {
    // given agentic data in tenant A, and a user authorized for it who selects an unknown process
    persistInstancesInTenant(agenticInstance(), uniqueKey(), TENANT_A, 3);
    authorizeCurrentUserFor(TENANT_A);

    // when
    final Double result = reports.evaluateNumber(KPI_COMPLETED_REPORT_ID, selecting(uniqueKey()));

    // then
    assertThat(countOf(result)).isZero();
  }

  @Test
  void shouldRejectAgenticSelectionOfProcessOnlyInUnauthorizedTenant() {
    // given an agentic process that only exists in tenant B, and a user authorized for tenant A
    final String processKey = uniqueKey();
    persistInstancesInTenant(agenticInstance(), processKey, TENANT_B, 3);
    authorizeCurrentUserFor(TENANT_A);

    // when the user selects that process
    // then
    assertThatThrownBy(() -> reports.evaluateNumber(KPI_COMPLETED_REPORT_ID, selecting(processKey)))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void shouldOnlyReturnAgenticDataFromAuthorizedTenants() {
    // given an agentic process deployed to both tenants, and one that only exists in tenant B
    final String sharedKey = uniqueKey();
    persistInstancesInTenant(agenticInstance(), sharedKey, TENANT_A, 2);
    persistInstancesInTenant(agenticInstance(), sharedKey, TENANT_B, 3);
    persistInstancesInTenant(agenticInstance(), uniqueKey(), TENANT_B, 4);
    authorizeCurrentUserFor(TENANT_A);

    // when evaluating the completed-runs tile without a process selected
    final Double result = reports.evaluateNumber(KPI_COMPLETED_REPORT_ID, noExtraFilters());

    // then only tenant A's instances are counted
    assertThat(result).isEqualTo(2.0);
  }

  // --- management report ---

  @Test
  void shouldReturnNoManagementDataWhenUserTenantsHoldNoDefinitions() {
    // given data in tenant A, and a user only authorized for a tenant without any definitions
    persistInstancesInTenant(plainInstance(), uniqueKey(), TENANT_A, 3);
    authorizeCurrentUserFor(TENANT_WITHOUT_DATA);
    final ProcessReportDataDto report = instanceCountReport(List.of());
    report.setManagementReport(true);

    // when
    final double result = evaluateUnsavedCount(report);

    // then
    assertThat(result).isZero();
  }

  @Test
  void shouldOnlyReturnManagementDataFromAuthorizedTenants() {
    // given a process deployed to both tenants, and a process that only exists in tenant B
    final String sharedKey = uniqueKey();
    persistInstancesInTenant(plainInstance(), sharedKey, TENANT_A, 2);
    persistInstancesInTenant(plainInstance(), sharedKey, TENANT_B, 3);
    persistInstancesInTenant(plainInstance(), uniqueKey(), TENANT_B, 4);
    authorizeCurrentUserFor(TENANT_A);
    final ProcessReportDataDto report = instanceCountReport(List.of());
    report.setManagementReport(true);

    // when
    final double result = evaluateUnsavedCount(report);

    // then only tenant A's instances are counted
    assertThat(result).isEqualTo(2.0);
  }

  // --- instant preview report ---

  @Test
  void shouldRejectInstantPreviewOfProcessOnlyInUnauthorizedTenant() {
    // given a process that only exists in tenant B, and a user only authorized for tenant A
    final String processKey = uniqueKey();
    persistInstancesInTenant(plainInstance(), processKey, TENANT_B, 3);
    authorizeCurrentUserFor(TENANT_A);
    final ProcessReportDataDto report =
        instanceCountReport(List.of(new ReportDataDefinitionDto(processKey)));
    report.setInstantPreviewReport(true);

    // when previewing that process
    // then
    assertThatThrownBy(() -> evaluateUnsavedCount(report)).isInstanceOf(ForbiddenException.class);
  }

  @Test
  void shouldOnlyReturnInstantPreviewDataFromAuthorizedTenants() {
    // given a process deployed to both tenants, and a user only authorized for tenant A
    final String processKey = uniqueKey();
    persistInstancesInTenant(plainInstance(), processKey, TENANT_A, 2);
    persistInstancesInTenant(plainInstance(), processKey, TENANT_B, 3);
    authorizeCurrentUserFor(TENANT_A);
    final ProcessReportDataDto report =
        instanceCountReport(List.of(new ReportDataDefinitionDto(processKey)));
    report.setInstantPreviewReport(true);

    // when
    final double result = evaluateUnsavedCount(report);

    // then only tenant A's instances are counted
    assertThat(result).isEqualTo(2.0);
  }

  private double evaluateUnsavedCount(final ProcessReportDataDto reportData) {
    final SingleReportEvaluationResult<?> result =
        (SingleReportEvaluationResult<?>)
            reportEvaluationService
                .evaluateUnsavedReport(
                    USER_ID, UTC, new SingleProcessReportDefinitionRequestDto(reportData), null)
                .getEvaluationResult();
    final List<MeasureDto<Double>> measures =
        ((NumberCommandResult) result.getFirstCommandResult()).getMeasures();
    return countOf(measures.isEmpty() ? null : measures.getFirst().getData());
  }

  private static ProcessReportDataDto instanceCountReport(
      final List<ReportDataDefinitionDto> definitions) {
    return ProcessReportDataDto.builder()
        .definitions(definitions)
        .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.FREQUENCY))
        .groupBy(new NoneGroupByDto())
        .visualization(ProcessVisualization.NUMBER)
        .build();
  }

  // A count over no instances may come back without a measure value rather than as zero.
  private static double countOf(final Double result) {
    return result == null ? 0.0 : result;
  }

  private static AdditionalProcessReportEvaluationFilterDto selecting(final String processKey) {
    return withDefinitions(List.of(new ReportDataDefinitionDto(processKey)));
  }

  // Tenant authorizations are cached per user id, so every test authorizes a fresh user.
  private void authorizeCurrentUserFor(final String tenantId) {
    final String userId = "user-" + UUID.randomUUID();
    final String token = "token-" + userId;
    final CCSMTokenService tokenService = mock(CCSMTokenService.class);
    when(tokenService.getCurrentUserIdFromAuthToken()).thenReturn(Optional.of(userId));
    when(tokenService.getCurrentUserAuthToken()).thenReturn(Optional.of(token));
    when(tokenService.getAuthorizedTenantsFromToken(token))
        .thenReturn(List.of(new TenantDto(tenantId, tenantId, ZEEBE_DATA_SOURCE)));
    ReflectionTestUtils.setField(tenantAuthorizationService, "ccsmTokenService", tokenService);
  }

  // persistProcessInstances writes each definition under the default tenant, so the definition is
  // rewritten afterwards under the instances' tenant.
  private void persistInstancesInTenant(
      final Function<String, ProcessInstanceDto.ProcessInstanceDtoBuilder> instanceFactory,
      final String processKey,
      final String tenantId,
      final int instanceCount) {
    final String definitionId = processKey + ":1:" + tenantId;
    persistProcessInstances(
        IntStream.range(0, instanceCount)
            .mapToObj(
                i ->
                    instanceFactory
                        .apply(processKey)
                        .processDefinitionId(definitionId)
                        .tenantId(tenantId)
                        .build())
            .toList());
    persistProcessDefinitions(
        List.of(
            ProcessDefinitionOptimizeDto.builder()
                .id(definitionId)
                .key(processKey)
                .version("1")
                .name(processKey)
                .dataSource(new ZeebeDataSourceDto("test-source", 1))
                .tenantId(tenantId)
                .bpmn20Xml("<definitions/>")
                .build()));
  }

  private static Function<String, ProcessInstanceDto.ProcessInstanceDtoBuilder> plainInstance() {
    return AbstractBrokerlessZeebeCCSMIT::completedInstance;
  }

  private static Function<String, ProcessInstanceDto.ProcessInstanceDtoBuilder> agenticInstance() {
    return key -> agenticInstanceWithTokens(key, 10L, 5L);
  }

  private static String uniqueKey() {
    return "tenant-isolation-" + UUID.randomUUID();
  }
}
