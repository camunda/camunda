/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.filter.AuthorizationFilter;
import io.camunda.search.filter.BatchOperationFilter;
import io.camunda.search.filter.BatchOperationItemFilter;
import io.camunda.search.filter.DecisionDefinitionFilter;
import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.filter.DecisionRequirementsFilter;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import io.camunda.search.filter.FormFilter;
import io.camunda.search.filter.GroupFilter;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.search.filter.JobFilter;
import io.camunda.search.filter.MappingRuleFilter;
import io.camunda.search.filter.ProcessDefinitionFilter;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.filter.RoleFilter;
import io.camunda.search.filter.TenantFilter;
import io.camunda.search.filter.UserFilter;
import io.camunda.search.filter.UserTaskFilter;
import io.camunda.search.filter.VariableFilter;
import java.io.File;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * This test checks that all properties of the filter records are present in the corresponding
 * MyBatis mapper XML files, specifically in the `<sql id="searchFilter">` section. It ensures that
 * the SQL queries generated for searching these filters include all necessary properties defined in
 * the filter records.
 *
 * <p>This test is a hacky and opinionated implementation and can be removed when it makes too many
 * problems since it does not really verify production behaviour of the code but is just a developer
 * tool.
 */
@Disabled
public class SearchFilterPropertiesInMapperTest {

  static Stream<TestCase> filterAndMapperProvider() {
    // All filter record classes and their corresponding mapper XML file names
    return Stream.of(
        new TestCase(AuthorizationFilter.class, "AuthorizationsMapper.xml"),
        new TestCase(
            BatchOperationFilter.class,
            "BatchOperationMapper.xml",
            "searchFilter",
            List.of("itemKeyOperations", "processInstanceKeyOperations")),
        new TestCase(
            BatchOperationItemFilter.class,
            "BatchOperationMapper.xml",
            "itemSearchFilter",
            List.of()),
        new TestCase(DecisionDefinitionFilter.class, "DecisionDefinitionMapper.xml"),
        new TestCase(DecisionInstanceFilter.class, "DecisionInstanceMapper.xml"),
        new TestCase(DecisionRequirementsFilter.class, "DecisionRequirementsMapper.xml"),
        new TestCase(FlowNodeInstanceFilter.class, "FlowNodeInstanceMapper.xml"),
        new TestCase(FormFilter.class, "FormMapper.xml"),
        new TestCase(GroupFilter.class, "GroupMapper.xml"),
        new TestCase(IncidentFilter.class, "IncidentMapper.xml"),
        new TestCase(JobFilter.class, "JobMapper.xml"),
        new TestCase(MappingRuleFilter.class, "MappingRuleMapper.xml"),
        new TestCase(ProcessDefinitionFilter.class, "ProcessDefinitionMapper.xml"),
        new TestCase(ProcessInstanceFilter.class, "ProcessInstanceMapper.xml"),
        new TestCase(RoleFilter.class, "RoleMapper.xml"),
        new TestCase(TenantFilter.class, "TenantMapper.xml"),
        new TestCase(UserFilter.class, "UserMapper.xml"),
        new TestCase(UserTaskFilter.class, "UserTaskMapper.xml"),
        new TestCase(VariableFilter.class, "VariableMapper.xml"));
  }

  @ParameterizedTest
  @MethodSource("filterAndMapperProvider")
  void allFilterPropertiesPresentInSearchFilterSql(final TestCase testCase) throws Exception {
    // 1. Get all property names from the filter record, excluding those in excludedProperties
    final var propertyNames =
        Arrays.stream(testCase.filterRecord().getRecordComponents())
            .map(RecordComponent::getName)
            .filter(name -> !testCase.excludedProperties().contains(name))
            .toList();

    // 2. Load the MyBatis XML file as a string
    final var xmlFile = new File("src/main/resources/mapper/" + testCase.mapperXmlFile());
    assertThat(xmlFile)
        .withFailMessage("Mapper XML file does not exist: %s", xmlFile.getPath())
        .exists();
    final var xmlContent = Files.readString(xmlFile.toPath());

    // 3. Use regex to extract the <sql id="searchFilter">...</sql> block
    final var pattern =
        Pattern.compile(
            "<sql\\s+id=\\\"" + testCase.searchFilterIncludeId + "\\\"[^>]*>(.*?)</sql>",
            Pattern.DOTALL);
    final var matcher = pattern.matcher(xmlContent);
    String searchFilterSql = null;
    if (matcher.find()) {
      searchFilterSql = matcher.group(1);
    }
    assertThat(searchFilterSql)
        .withFailMessage(
            "No <sql id=\"" + testCase.searchFilterIncludeId + "\"> found in %s",
            testCase.mapperXmlFile())
        .isNotNull();

    // 4. Check that each property is present in the searchFilter SQL/XML
    final var missingProperties = new ArrayList<String>();
    for (final var property : propertyNames) {
      if (!searchFilterSql.contains("filter." + property)) {
        missingProperties.add(property);
      }
    }
    assertThat(missingProperties)
        .withFailMessage(
            "The following properties are missing in searchFilter SQL of %s: %s",
            testCase.mapperXmlFile(), String.join(", ", missingProperties))
        .isEmpty();
  }

  record TestCase(
      Class<?> filterRecord,
      String mapperXmlFile,
      String searchFilterIncludeId,
      List<String> excludedProperties) {
    TestCase(final Class<?> filterRecord, final String mapperXmlFile) {
      this(filterRecord, mapperXmlFile, "searchFilter", List.of());
    }
  }
}
