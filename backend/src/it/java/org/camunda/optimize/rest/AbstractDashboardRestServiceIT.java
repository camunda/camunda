/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardAssigneeFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardCandidateGroupFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardInstanceEndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardInstanceStartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardStateFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardBooleanVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardDateVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardDoubleVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardIdentityFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardIntegerVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardLongVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardShortVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardStateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardStringVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.FixedDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RelativeDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.DashboardVariableFilterSubDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.CONTAINS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_CONTAINS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_IN;

public abstract class AbstractDashboardRestServiceIT extends AbstractIT {

  protected static Stream<List<DashboardFilterDto<?>>> validFilterCombinations() {
    return Stream.of(
      null,
      Collections.emptyList(),
      Collections.singletonList(createDashboardStartDateFilterWithDefaultValues(null)),
      Arrays.asList(
        createDashboardStartDateFilterWithDefaultValues(null),
        createDashboardEndDateFilterWithDefaultValues(null)
      ),
      Arrays.asList(
        createDashboardStartDateFilterWithDefaultValues(null),
        createDashboardEndDateFilterWithDefaultValues(null),
        createDashboardStateFilterWithDefaultValues(null)
      ),
      Collections.singletonList(createDashboardVariableFilter(VariableType.BOOLEAN, "boolVar")),
      Collections.singletonList(createDashboardVariableFilter(VariableType.DATE, "dateVar")),
      variableFilter(),
      Collections.singletonList(createDashboardVariableFilter(
        VariableType.LONG, "longVar", IN, Arrays.asList("1", "2"), false)),
      Collections.singletonList(createDashboardVariableFilter(
        VariableType.SHORT, "shortVar", IN, Arrays.asList("1", "2"), false)),
      Collections.singletonList(createDashboardVariableFilter(
        VariableType.INTEGER, "integerVar", IN, Arrays.asList("1", "2"), false)),
      Collections.singletonList(createDashboardVariableFilter(
        VariableType.INTEGER, "integerVar", IN, Arrays.asList("1", "2"), true)),
      Collections.singletonList(createDashboardVariableFilter(
        VariableType.LONG, "longVar", IN, Arrays.asList("1", "2"), true)),
      Collections.singletonList(createDashboardVariableFilter(
        VariableType.SHORT, "shortVar", IN, Arrays.asList("1", "2"), true)),
      Arrays.asList(
        createDashboardVariableFilter(VariableType.LONG, "longVar", IN, Arrays.asList("1", "2"), false),
        createDashboardVariableFilter(VariableType.DOUBLE, "doubleVar", IN, Arrays.asList("1", "2"), true)
      ),
      Arrays.asList(
        createDashboardAssigneeFilter(Collections.emptyList(), MembershipFilterOperator.IN, false),
        createDashboardCandidateGroupFilter(Collections.emptyList(), MembershipFilterOperator.NOT_IN, true)
      ),
      Arrays.asList(
        createDashboardAssigneeFilter(Arrays.asList("Rose", "Martha"), MembershipFilterOperator.IN, false),
        createDashboardAssigneeFilter(Arrays.asList("Donna", "Clara"), MembershipFilterOperator.NOT_IN, true)
      ),
      Arrays.asList(
        createDashboardCandidateGroupFilter(Arrays.asList("Cybermen", "Daleks"), MembershipFilterOperator.IN, true),
        createDashboardCandidateGroupFilter(Arrays.asList("Ood", "Judoon"), MembershipFilterOperator.NOT_IN, false)
      ),
      Arrays.asList(
        createDashboardAssigneeFilter(Arrays.asList("Rose", "Martha"), MembershipFilterOperator.IN, true),
        createDashboardCandidateGroupFilter(Arrays.asList("Ood", "Judoon"), MembershipFilterOperator.NOT_IN, true)
      ),
      Arrays.asList(
        createDashboardVariableFilter(VariableType.BOOLEAN, "boolVar"),
        createDashboardVariableFilter(VariableType.DATE, "dateVar"),
        createDashboardVariableFilter(VariableType.LONG, "longVar", IN, Arrays.asList("1", "2"), false),
        createDashboardVariableFilter(VariableType.DOUBLE, "doubleVar", IN, Arrays.asList("1.0", "2.0"), false),
        createDashboardVariableFilter(VariableType.STRING, "stringVar", IN, Arrays.asList("StringA", "StringB"),
                                      false
        ),
        createDashboardVariableFilter(
          VariableType.STRING,
          "stringVar",
          CONTAINS,
          Arrays.asList("StringA", "StringB"),
          false
        ),
        createDashboardVariableFilter(
          VariableType.STRING,
          "stringVar",
          NOT_CONTAINS,
          Collections.singletonList("foo"),
          false
        ),
        createDashboardAssigneeFilter(Arrays.asList("Rose", "Martha"), MembershipFilterOperator.IN, false),
        createDashboardCandidateGroupFilter(Arrays.asList("Cybermen", "Daleks"), MembershipFilterOperator.IN, false),
        createDashboardStartDateFilterWithDefaultValues(null),
        createDashboardEndDateFilterWithDefaultValues(null),
        createDashboardStateFilterWithDefaultValues(null)
      ),
      Arrays.asList(
        createDashboardVariableFilter(VariableType.BOOLEAN, "boolVar"),
        createDashboardVariableFilter(VariableType.DATE, "dateVar"),
        createDashboardVariableFilter(VariableType.LONG, "longVar", IN, Arrays.asList("1", "2"), true),
        createDashboardVariableFilter(VariableType.DOUBLE, "doubleVar", IN, Arrays.asList("1.0", "2.0"), true),
        createDashboardVariableFilter(VariableType.STRING, "stringVar", IN, Arrays.asList("StringA", "StringB"), true),
        createDashboardVariableFilter(
          VariableType.STRING,
          "stringVar",
          CONTAINS,
          Arrays.asList("StringA", "StringB"),
          false
        ),
        createDashboardVariableFilter(
          VariableType.STRING,
          "stringVar",
          NOT_CONTAINS,
          Collections.singletonList("foo"),
          false
        ),
        createDashboardAssigneeFilter(Arrays.asList("Rose", "Martha"), MembershipFilterOperator.IN, true),
        createDashboardCandidateGroupFilter(Arrays.asList("Cybermen", "Daleks"), MembershipFilterOperator.IN, true),
        createDashboardStartDateFilterWithDefaultValues(null),
        createDashboardEndDateFilterWithDefaultValues(null),
        createDashboardStateFilterWithDefaultValues(null)
      ),
      Arrays.asList(
        createDashboardDateVariableFilterWithDefaultValues(
          new FixedDateFilterDataDto(
            OffsetDateTime.parse("2021-06-07T18:00:00+02:00"),
            OffsetDateTime.parse("2021-06-08T18:00:00+02:00")
          )
        ),
        createDashboardDateVariableFilterWithDefaultValues(
          new RollingDateFilterDataDto(new RollingDateFilterStartDto(1L, DateUnit.YEARS))
        ),
        createDashboardDateVariableFilterWithDefaultValues(
          new RelativeDateFilterDataDto(new RelativeDateFilterStartDto(2L, DateUnit.MINUTES))
        ),
        createDashboardBooleanVariableFilterWithDefaultValues(),
        createDashboardStringVariableFilterWithDefaultValues(),
        createDashboardShortVariableFilterWithDefaultValues(),
        createDashboardLongVariableFilterWithDefaultValues(),
        createDashboardDoubleVariableFilterWithDefaultValues(),
        createDashboardIntegerVariableFilterWithDefaultValues(),
        createDashboardStateFilterWithDefaultValues(Collections.singletonList("canceledInstancesOnly")),
        createDashboardStartDateFilterWithDefaultValues(
          new FixedDateFilterDataDto(
            OffsetDateTime.parse("2021-06-07T18:00:00+02:00"),
            OffsetDateTime.parse("2021-06-08T18:00:00+02:00")
          )),
        createDashboardEndDateFilterWithDefaultValues(
          new FixedDateFilterDataDto(
            OffsetDateTime.parse("2021-06-05T18:00:00+02:00"),
            OffsetDateTime.parse("2021-06-06T18:00:00+02:00")
          )),
        createDashboardAssigneeFilter(
          Arrays.asList("Rose", "Martha"),
          MembershipFilterOperator.IN,
          true,
          Collections.singletonList("Martha")
        ),
        createDashboardCandidateGroupFilter(
          Arrays.asList("Cybermen", "Daleks"),
          MembershipFilterOperator.NOT_IN,
          true,
          Arrays.asList("Cybermen", "Daleks")
        )
      ),
      Collections.singletonList(
        createDashboardEndDateFilterWithDefaultValues(
          new RelativeDateFilterDataDto(new RelativeDateFilterStartDto(1L, DateUnit.MINUTES))
        )
      ),
      Collections.singletonList(
        createDashboardEndDateFilterWithDefaultValues(
          new RollingDateFilterDataDto(new RollingDateFilterStartDto(2L, DateUnit.YEARS))
        )
      ),
      Collections.singletonList(
        createDashboardStartDateFilterWithDefaultValues(
          new RelativeDateFilterDataDto(new RelativeDateFilterStartDto(3L, DateUnit.SECONDS))
        )
      ),
      Collections.singletonList(
        createDashboardStartDateFilterWithDefaultValues(
          new RollingDateFilterDataDto(new RollingDateFilterStartDto(4L, DateUnit.DAYS))
        )
      )
    );
  }

  protected static Stream<List<DashboardFilterDto>> invalidFilterCombinations() {
    return Stream.of(
      Collections.singletonList(new DashboardVariableFilterDto()),
      Collections.singletonList(new DashboardAssigneeFilterDto()),
      Collections.singletonList(new DashboardCandidateGroupFilterDto()),
      Arrays.asList(
        createDashboardAssigneeFilter(Collections.emptyList(), MembershipFilterOperator.IN, false),
        createDashboardAssigneeFilter(Collections.emptyList(), MembershipFilterOperator.IN, true)
      ),
      Arrays.asList(
        createDashboardCandidateGroupFilter(Collections.emptyList(), MembershipFilterOperator.NOT_IN, false),
        createDashboardCandidateGroupFilter(Collections.emptyList(), MembershipFilterOperator.NOT_IN, true)
      ),
      Arrays.asList(
        new DashboardInstanceStartDateFilterDto(),
        new DashboardInstanceStartDateFilterDto()
      ),
      Arrays.asList(
        new DashboardInstanceEndDateFilterDto(),
        new DashboardInstanceEndDateFilterDto()
      ),
      Arrays.asList(
        new DashboardStateFilterDto(),
        new DashboardStateFilterDto()
      ),
      Collections.singletonList(createDashboardVariableFilter(
        VariableType.DATE, "dateVar", IN, Collections.singletonList(OffsetDateTime.now().toString()), false)),
      Collections.singletonList(createDashboardVariableFilter(
        VariableType.DATE, "dateVar", IN, Collections.singletonList(OffsetDateTime.now().toString()), true)),
      Collections.singletonList(createDashboardVariableFilter(
        VariableType.BOOLEAN, "boolVar", IN, Collections.singletonList("true"), false)),
      Collections.singletonList(createDashboardVariableFilter(
        VariableType.BOOLEAN, "boolVar", IN, Collections.singletonList("true"), true)),
      Collections.singletonList(createDashboardVariableFilter(VariableType.LONG, "longVar", null)),
      Collections.singletonList(createDashboardVariableFilter(VariableType.DOUBLE, "doubleVar", null)),
      Collections.singletonList(createDashboardVariableFilter(VariableType.STRING, "stringVar", null))
    );
  }

  protected DashboardDefinitionRestDto generateDashboardDefinitionDto() {
    DashboardDefinitionRestDto dashboardDefinitionDto = new DashboardDefinitionRestDto();
    dashboardDefinitionDto.setName("Dashboard name");
    return dashboardDefinitionDto;
  }

  protected DashboardDefinitionRestDto createDashboardForReportContainingAllVariables(
    final List<DashboardFilterDto<?>> dashboardFilterDtos) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("someProcess").startEvent().endEvent().done();
    final ProcessInstanceEngineDto deployedInstanceWithAllVariables =
      engineIntegrationExtension.deployAndStartProcessWithVariables(
        modelInstance,
        Map.of(
          "boolVar", true,
          "dateVar", OffsetDateTime.now(),
          "longVar", 1L,
          "shortVar", (short) 2,
          "integerVar", 3,
          "doubleVar", 4.0D,
          "stringVar", "sillyString"
        )
      );
    importAllEngineEntitiesFromScratch();
    final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      reportClient.createSingleProcessReportDefinitionDto(
        null,
        deployedInstanceWithAllVariables.getProcessDefinitionKey(),
        Collections.singletonList(null)
      );
    singleProcessReportDefinitionDto.getData()
      .setProcessDefinitionVersion(deployedInstanceWithAllVariables.getProcessDefinitionVersion());
    final String reportId = reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
    final DashboardDefinitionRestDto dashboardDefinitionDto = generateDashboardDefinitionDto();
    dashboardDefinitionDto.setTiles(Collections.singletonList(
      DashboardReportTileDto.builder().id(reportId).type(DashboardTileType.OPTIMIZE_REPORT).build()));
    dashboardDefinitionDto.setAvailableFilters(dashboardFilterDtos);
    return dashboardDefinitionDto;
  }

  protected static DashboardFilterDto<?> createDashboardVariableFilter(final VariableType type,
                                                                       final String variableName,
                                                                       final FilterOperator operator,
                                                                       final List<String> values,
                                                                       final boolean allowCustomValues) {
    switch (type) {
      case LONG:
      case SHORT:
      case DOUBLE:
      case STRING:
      case INTEGER:
        return createDashboardVariableFilter(
          type,
          variableName,
          new DashboardVariableFilterSubDataDto(operator, values, allowCustomValues)
        );
      case DATE:
      case BOOLEAN:
        return createDashboardVariableFilter(type, variableName, null);
      default:
        throw new OptimizeIntegrationTestException("Unknown variable type: " + type);
    }
  }

  protected static List<DashboardFilterDto<?>> variableFilter() {
    final DashboardVariableFilterDto variableFilter = new DashboardVariableFilterDto();
    variableFilter.setData(new DashboardDateVariableFilterDataDto("dateVar"));
    return Collections.singletonList(variableFilter);
  }

  private static DashboardFilterDto<?> createDashboardVariableFilter(final VariableType type,
                                                                     final String variableName) {
    return createDashboardVariableFilter(type, variableName, null);
  }

  private static DashboardFilterDto<?> createDashboardVariableFilter(final VariableType type,
                                                                     final String variableName,
                                                                     final DashboardVariableFilterSubDataDto subData) {
    final DashboardVariableFilterDto variableFilter = new DashboardVariableFilterDto();
    DashboardVariableFilterDataDto filterData;
    switch (type) {
      case DATE:
        filterData = new DashboardDateVariableFilterDataDto(variableName);
        break;
      case LONG:
        filterData = new DashboardLongVariableFilterDataDto(variableName, subData);
        break;
      case SHORT:
        filterData = new DashboardShortVariableFilterDataDto(variableName, subData);
        break;
      case DOUBLE:
        filterData = new DashboardDoubleVariableFilterDataDto(variableName, subData);
        break;
      case STRING:
        filterData = new DashboardStringVariableFilterDataDto(variableName, subData);
        break;
      case BOOLEAN:
        filterData = new DashboardBooleanVariableFilterDataDto(variableName);
        break;
      case INTEGER:
        filterData = new DashboardIntegerVariableFilterDataDto(variableName, subData);
        break;
      default:
        throw new OptimizeIntegrationTestException("Unknown variable type: " + type);
    }
    variableFilter.setData(filterData);
    return variableFilter;
  }

  private static DashboardFilterDto<?> createDashboardDateVariableFilterWithDefaultValues(final DateFilterDataDto<?> defaultValues) {
    DashboardVariableFilterDto filterDto =
      (DashboardVariableFilterDto) createDashboardVariableFilter(VariableType.DATE, "dateVar", null);
    ((DashboardDateVariableFilterDataDto) filterDto.getData()).setDefaultValues(defaultValues);
    return filterDto;
  }

  private static DashboardFilterDto<?> createDashboardBooleanVariableFilterWithDefaultValues() {
    DashboardVariableFilterDto filterDto =
      (DashboardVariableFilterDto) createDashboardVariableFilter(VariableType.BOOLEAN, "boolVar", null);
    ((DashboardBooleanVariableFilterDataDto) filterDto.getData()).setDefaultValues(Collections.singletonList(true));
    return filterDto;
  }

  private static DashboardFilterDto<?> createDashboardStringVariableFilterWithDefaultValues() {
    DashboardVariableFilterDto filterDto =
      (DashboardVariableFilterDto) createDashboardVariableFilter(
        VariableType.STRING,
        "stringVar",
        IN,
        List.of("aStringValue", "anotherStringValue"),
        false
      );
    ((DashboardStringVariableFilterDataDto) filterDto.getData()).setDefaultValues(Collections.singletonList(
      "aStringValue"));
    return filterDto;
  }

  private static DashboardFilterDto<?> createDashboardIntegerVariableFilterWithDefaultValues() {
    DashboardVariableFilterDto filterDto =
      (DashboardVariableFilterDto) createDashboardVariableFilter(
        VariableType.INTEGER,
        "integerVar",
        NOT_IN,
        List.of("7", "8"),
        false
      );
    ((DashboardIntegerVariableFilterDataDto) filterDto.getData()).setDefaultValues(Collections.singletonList("8"));
    return filterDto;
  }

  private static DashboardFilterDto<?> createDashboardShortVariableFilterWithDefaultValues() {
    DashboardVariableFilterDto filterDto =
      (DashboardVariableFilterDto) createDashboardVariableFilter(
        VariableType.SHORT,
        "shortVar",
        IN,
        List.of("1", "2"),
        false
      );
    ((DashboardShortVariableFilterDataDto) filterDto.getData()).setDefaultValues(Collections.singletonList("1"));
    return filterDto;
  }

  private static DashboardFilterDto<?> createDashboardLongVariableFilterWithDefaultValues() {
    DashboardVariableFilterDto filterDto =
      (DashboardVariableFilterDto) createDashboardVariableFilter(
        VariableType.LONG,
        "longVar",
        IN,
        List.of("3", "4"),
        false
      );
    ((DashboardLongVariableFilterDataDto) filterDto.getData()).setDefaultValues(Collections.singletonList("4"));
    return filterDto;
  }

  private static DashboardFilterDto<?> createDashboardDoubleVariableFilterWithDefaultValues() {
    DashboardVariableFilterDto filterDto =
      (DashboardVariableFilterDto) createDashboardVariableFilter(
        VariableType.DOUBLE,
        "doubleVar",
        NOT_IN,
        List.of("5.0", "6.0"),
        false
      );
    ((DashboardDoubleVariableFilterDataDto) filterDto.getData()).setDefaultValues(Collections.singletonList("5.0"));
    return filterDto;
  }

  private static DashboardFilterDto<?> createDashboardStartDateFilterWithDefaultValues(final DateFilterDataDto<?> defaultValues) {
    DashboardInstanceStartDateFilterDto filterDto = new DashboardInstanceStartDateFilterDto();
    filterDto.setData(new DashboardDateFilterDataDto(defaultValues));
    return filterDto;
  }

  private static DashboardFilterDto<?> createDashboardEndDateFilterWithDefaultValues(final DateFilterDataDto<?> defaultValues) {
    DashboardInstanceEndDateFilterDto filterDto = new DashboardInstanceEndDateFilterDto();
    filterDto.setData(new DashboardDateFilterDataDto(defaultValues));
    return filterDto;
  }

  private static DashboardFilterDto<?> createDashboardStateFilterWithDefaultValues(final List<String> defaultValues) {
    DashboardStateFilterDto filterDto = new DashboardStateFilterDto();
    filterDto.setData(new DashboardStateFilterDataDto(defaultValues));
    return filterDto;
  }

  private static DashboardFilterDto<?> createDashboardAssigneeFilter(final List<String> assigneeNames,
                                                                     final MembershipFilterOperator filterOperator,
                                                                     final boolean allowCustomValues) {
    return createDashboardAssigneeFilter(assigneeNames, filterOperator, allowCustomValues, null);
  }

  private static DashboardFilterDto<?> createDashboardAssigneeFilter(final List<String> assigneeNames,
                                                                     final MembershipFilterOperator filterOperator,
                                                                     final boolean allowCustomValues,
                                                                     final List<String> defaultValues) {
    final DashboardAssigneeFilterDto assigneeFilter = new DashboardAssigneeFilterDto();
    assigneeFilter.setData(new DashboardIdentityFilterDataDto(
      filterOperator,
      assigneeNames,
      allowCustomValues,
      defaultValues
    ));
    return assigneeFilter;
  }

  private static DashboardFilterDto<?> createDashboardCandidateGroupFilter(final List<String> candidateGroupNames,
                                                                           final MembershipFilterOperator filterOperator,
                                                                           final boolean allowCustomValues) {
    return createDashboardCandidateGroupFilter(candidateGroupNames, filterOperator, allowCustomValues, null);
  }

  private static DashboardFilterDto<?> createDashboardCandidateGroupFilter(final List<String> candidateGroupNames,
                                                                           final MembershipFilterOperator filterOperator,
                                                                           final boolean allowCustomValues,
                                                                           final List<String> defaultValues) {
    final DashboardCandidateGroupFilterDto candidateGroupFilter = new DashboardCandidateGroupFilterDto();
    candidateGroupFilter.setData(new DashboardIdentityFilterDataDto(
      filterOperator,
      candidateGroupNames,
      allowCustomValues,
      defaultValues
    ));
    return candidateGroupFilter;
  }

  protected static Stream<DashboardReportTileDto> getInvalidReportIdAndTypes() {
    return Stream.of(
      DashboardReportTileDto.builder().id(null).type(DashboardTileType.OPTIMIZE_REPORT).build(),
      DashboardReportTileDto.builder().id("someId").type(DashboardTileType.EXTERNAL_URL).build(),
      DashboardReportTileDto.builder().id("someId").type(DashboardTileType.TEXT).build()
    );
  }

}
