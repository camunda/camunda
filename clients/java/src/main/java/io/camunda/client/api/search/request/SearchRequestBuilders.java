/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.client.api.search.request;

import io.camunda.client.api.search.filter.AdHocSubprocessActivityFilter;
import io.camunda.client.api.search.filter.DecisionDefinitionFilter;
import io.camunda.client.api.search.filter.DecisionInstanceFilter;
import io.camunda.client.api.search.filter.DecisionRequirementsFilter;
import io.camunda.client.api.search.filter.FlownodeInstanceFilter;
import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.client.api.search.filter.ProcessDefinitionFilter;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.client.api.search.filter.UserTaskVariableFilter;
import io.camunda.client.api.search.filter.VariableFilter;
import io.camunda.client.api.search.sort.DecisionDefinitionSort;
import io.camunda.client.api.search.sort.DecisionInstanceSort;
import io.camunda.client.api.search.sort.DecisionRequirementsSort;
import io.camunda.client.api.search.sort.FlownodeInstanceSort;
import io.camunda.client.api.search.sort.IncidentSort;
import io.camunda.client.api.search.sort.ProcessDefinitionSort;
import io.camunda.client.api.search.sort.ProcessInstanceSort;
import io.camunda.client.api.search.sort.UserTaskSort;
import io.camunda.client.api.search.sort.VariableSort;
import io.camunda.client.impl.search.SearchRequestPageImpl;
import io.camunda.client.impl.search.filter.AdHocSubprocessActivityFilterImpl;
import io.camunda.client.impl.search.filter.DecisionDefinitionFilterImpl;
import io.camunda.client.impl.search.filter.DecisionInstanceFilterImpl;
import io.camunda.client.impl.search.filter.DecisionRequirementsFilterImpl;
import io.camunda.client.impl.search.filter.FlownodeInstanceFilterImpl;
import io.camunda.client.impl.search.filter.IncidentFilterImpl;
import io.camunda.client.impl.search.filter.ProcessDefinitionFilterImpl;
import io.camunda.client.impl.search.filter.ProcessInstanceFilterImpl;
import io.camunda.client.impl.search.filter.UserTaskFilterImpl;
import io.camunda.client.impl.search.filter.UserTaskVariableFilterImpl;
import io.camunda.client.impl.search.filter.VariableFilterImpl;
import io.camunda.client.impl.search.sort.DecisionDefinitionSortImpl;
import io.camunda.client.impl.search.sort.DecisionInstanceSortImpl;
import io.camunda.client.impl.search.sort.DecisionRequirementsSortImpl;
import io.camunda.client.impl.search.sort.FlownodeInstanceSortImpl;
import io.camunda.client.impl.search.sort.IncidentSortImpl;
import io.camunda.client.impl.search.sort.ProcessDefinitionSortImpl;
import io.camunda.client.impl.search.sort.ProcessInstanceSortImpl;
import io.camunda.client.impl.search.sort.UserTaskSortImpl;
import io.camunda.client.impl.search.sort.VariableSortImpl;
import java.util.function.Consumer;

public final class SearchRequestBuilders {

  private SearchRequestBuilders() {}

  /** Create a process definition filter. */
  public static ProcessDefinitionFilter processDefinitionFilter() {
    return new ProcessDefinitionFilterImpl();
  }

  /** Create a process definition filter by using a fluent builder. */
  public static ProcessDefinitionFilter processDefinitionFilter(
      final Consumer<ProcessDefinitionFilter> fn) {
    final ProcessDefinitionFilter filter = processDefinitionFilter();
    fn.accept(filter);
    return filter;
  }

  /** Create a process instance filter. */
  public static ProcessInstanceFilter processInstanceFilter() {
    return new ProcessInstanceFilterImpl();
  }

  /** Create a process instance filter by using a fluent builder. */
  public static ProcessInstanceFilter processInstanceFilter(
      final Consumer<ProcessInstanceFilter> fn) {
    final ProcessInstanceFilter filter = processInstanceFilter();
    fn.accept(filter);
    return filter;
  }

  /** Create a decision definition filter. */
  public static DecisionDefinitionFilter decisionDefinitionFilter() {
    return new DecisionDefinitionFilterImpl() {};
  }

  /** Create a decision definition filter by using a fluent builder. */
  public static DecisionDefinitionFilter decisionDefinitionFilter(
      final Consumer<DecisionDefinitionFilter> fn) {
    final DecisionDefinitionFilter filter = decisionDefinitionFilter();
    fn.accept(filter);
    return filter;
  }

  /** Create an incident filter. */
  public static IncidentFilter incidentFilter() {
    return new IncidentFilterImpl() {};
  }

  /** Create an incident filter by using a fluent builder. */
  public static IncidentFilter incidentFilter(final Consumer<IncidentFilter> fn) {
    final IncidentFilter filter = incidentFilter();
    fn.accept(filter);
    return filter;
  }

  /** Create a process definition sort option. */
  public static ProcessDefinitionSort processDefinitionSort() {
    return new ProcessDefinitionSortImpl();
  }

  /** Create a process definition sort option by using a fluent builder. */
  public static ProcessDefinitionSort processDefinitionSort(
      final Consumer<ProcessDefinitionSort> fn) {
    final ProcessDefinitionSort sort = processDefinitionSort();
    fn.accept(sort);
    return sort;
  }

  /** Create a process instance sort option. */
  public static ProcessInstanceSort processInstanceSort() {
    return new ProcessInstanceSortImpl();
  }

  /** Create a process instance sort option by using a fluent builder. */
  public static ProcessInstanceSort processInstanceSort(final Consumer<ProcessInstanceSort> fn) {
    final ProcessInstanceSort sort = processInstanceSort();
    fn.accept(sort);
    return sort;
  }

  /** Create a decision definition sort option. */
  public static DecisionDefinitionSort decisionDefinitionSort() {
    return new DecisionDefinitionSortImpl() {};
  }

  /** Create a decision definition sort option by using a fluent builder. */
  public static DecisionDefinitionSort decisionDefinitionSort(
      final Consumer<DecisionDefinitionSort> fn) {
    final DecisionDefinitionSort sort = decisionDefinitionSort();
    fn.accept(sort);
    return sort;
  }

  public static IncidentSort incidentSort() {
    return new IncidentSortImpl() {};
  }

  public static IncidentSort incidentSort(final Consumer<IncidentSort> fn) {
    final IncidentSort sort = incidentSort();
    fn.accept(sort);
    return sort;
  }

  /** Create a search page. */
  public static SearchRequestPage searchRequestPage() {
    return new SearchRequestPageImpl();
  }

  /** Create a search page by using a fluent builder. */
  public static SearchRequestPage searchRequestPage(final Consumer<SearchRequestPage> fn) {
    final SearchRequestPage filter = searchRequestPage();
    fn.accept(filter);
    return filter;
  }

  public static UserTaskFilter userTaskFilter() {
    return new UserTaskFilterImpl();
  }

  public static UserTaskFilter userTaskFilter(final Consumer<UserTaskFilter> fn) {
    final UserTaskFilter filter = userTaskFilter();
    fn.accept(filter);
    return filter;
  }

  public static UserTaskSort userTaskSort() {
    return new UserTaskSortImpl();
  }

  public static UserTaskSort userTaskSort(final Consumer<UserTaskSort> fn) {
    final UserTaskSort sort = userTaskSort();
    fn.accept(sort);
    return sort;
  }

  public static DecisionRequirementsFilter decisionRequirementsFilter() {
    return new DecisionRequirementsFilterImpl();
  }

  public static DecisionRequirementsFilter decisionRequirementsFilter(
      final Consumer<DecisionRequirementsFilter> fn) {
    final DecisionRequirementsFilter filter = decisionRequirementsFilter();
    fn.accept(filter);
    return filter;
  }

  public static DecisionRequirementsSort decisionRequirementsSort() {
    return new DecisionRequirementsSortImpl();
  }

  public static DecisionRequirementsSort decisionRequirementsSort(
      final Consumer<DecisionRequirementsSort> fn) {
    final DecisionRequirementsSort sort = decisionRequirementsSort();
    fn.accept(sort);
    return sort;
  }

  public static DecisionInstanceFilter decisionInstanceFilter(
      final Consumer<DecisionInstanceFilter> fn) {
    final DecisionInstanceFilter filter = decisionInstanceFilter();
    fn.accept(filter);
    return filter;
  }

  public static DecisionInstanceFilter decisionInstanceFilter() {
    return new DecisionInstanceFilterImpl();
  }

  public static DecisionInstanceSort decisionInstanceSort(final Consumer<DecisionInstanceSort> fn) {
    final DecisionInstanceSort sort = decisionInstanceSort();
    fn.accept(sort);
    return sort;
  }

  public static DecisionInstanceSort decisionInstanceSort() {
    return new DecisionInstanceSortImpl();
  }

  public static FlownodeInstanceFilter flowNodeInstanceFilter() {
    return new FlownodeInstanceFilterImpl();
  }

  public static FlownodeInstanceFilter flowNodeInstanceFilter(
      final Consumer<FlownodeInstanceFilter> fn) {
    final FlownodeInstanceFilter filter = flowNodeInstanceFilter();
    fn.accept(filter);
    return filter;
  }

  public static FlownodeInstanceSort flowNodeInstanceSort() {
    return new FlownodeInstanceSortImpl();
  }

  public static FlownodeInstanceSort flowNodeInstanceSort(final Consumer<FlownodeInstanceSort> fn) {
    final FlownodeInstanceSort sort = flowNodeInstanceSort();
    fn.accept(sort);
    return sort;
  }

  public static AdHocSubprocessActivityFilter adHocSubprocessActivityFilter() {
    return new AdHocSubprocessActivityFilterImpl();
  }

  public static AdHocSubprocessActivityFilter adHocSubprocessActivityFilter(
      final Consumer<AdHocSubprocessActivityFilter> fn) {
    final AdHocSubprocessActivityFilter filter = adHocSubprocessActivityFilter();
    fn.accept(filter);
    return filter;
  }

  public static VariableFilter variableFilter() {
    return new VariableFilterImpl();
  }

  public static VariableFilter variableFilter(final Consumer<VariableFilter> fn) {
    final VariableFilter filter = variableFilter();
    fn.accept(filter);
    return filter;
  }

  public static VariableSort variableSort() {
    return new VariableSortImpl();
  }

  public static VariableSort variableSort(final Consumer<VariableSort> fn) {
    final VariableSort sort = variableSort();
    fn.accept(sort);
    return sort;
  }

  public static UserTaskVariableFilter userTaskVariableFilter() {
    return new UserTaskVariableFilterImpl();
  }

  public static UserTaskVariableFilter userTaskVariableFilter(
      final Consumer<UserTaskVariableFilter> fn) {
    final UserTaskVariableFilter filter = userTaskVariableFilter();
    fn.accept(filter);
    return filter;
  }
}
