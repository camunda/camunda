/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Icon} from 'components';
import {loadVariables} from 'services';
import {t} from 'translation';

import {
  DateFilter,
  VariableFilter,
  AssigneeFilter,
  NodeFilter,
  DurationFilter,
  NodeDuration,
} from './modals';
import FilterList from './FilterList';
import {loadValues, filterIncompatibleExistingFilters} from './service';
import InstanceFilters from './InstanceFilters';
import ViewFilters from './ViewFilters';

import './Filter.scss';

export default class Filter extends React.Component {
  state = {
    newFilterType: null,
    editFilter: null,
  };

  openNewFilterModal = (type) => (evt) => {
    this.setState({newFilterType: type});
  };

  openEditFilterModal = (filter) => (evt) => {
    this.setState({editFilter: filter});
  };

  closeModal = () => {
    this.setState({newFilterType: null, editFilter: null});
  };

  getFilterModal = (type) => {
    switch (type) {
      case 'startDate':
      case 'endDate':
        return DateFilter;
      case 'variable':
        return VariableFilter;
      case 'processInstanceDuration':
        return DurationFilter;
      case 'flowNodeDuration':
        return NodeDuration;
      case 'executedFlowNodes':
      case 'executingFlowNodes':
      case 'canceledFlowNodes':
        return NodeFilter;
      case 'assignee':
      case 'candidateGroup':
        return AssigneeFilter;
      default:
        return () => null;
    }
  };

  getFilterConfig = (type) => {
    if (type === 'variable') {
      const {processDefinitionKey, processDefinitionVersions, tenantIds} = this.props;
      return {
        getVariables: async () =>
          await loadVariables({processDefinitionKey, processDefinitionVersions, tenantIds}),
        getValues: async (name, type, numResults, valueFilter) =>
          await loadValues(
            this.props.processDefinitionKey,
            this.props.processDefinitionVersions,
            this.props.tenantIds,
            name,
            type,
            0,
            numResults,
            valueFilter
          ),
      };
    }
  };

  editFilter = (newFilter) => {
    const filters = this.props.data;

    const index = filters.indexOf(filters.find((v) => this.state.editFilter.data === v.data));

    newFilter.filterLevel = this.props.filterLevel;
    this.props.onChange({filter: {[index]: {$set: newFilter}}}, true);
    this.closeModal();
  };

  addFilter = (newFilter) => {
    let filters = this.props.data;
    filters = filterIncompatibleExistingFilters(filters, newFilter.type, ['startDate']);
    filters = filterIncompatibleExistingFilters(filters, newFilter.type, ['endDate']);
    filters = filterIncompatibleExistingFilters(filters, newFilter.type, [
      'completedInstancesOnly',
    ]);
    filters = filterIncompatibleExistingFilters(filters, newFilter.type, ['runningInstancesOnly']);
    filters = filterIncompatibleExistingFilters(filters, newFilter.type, ['canceledInstancesOnly']);

    newFilter.filterLevel = this.props.filterLevel;
    this.props.onChange({filter: {$set: [...filters, newFilter]}}, true);
    this.closeModal();
  };

  deleteFilter = (oldFilter) => {
    this.props.onChange(
      {
        filter: {$set: this.props.data.filter((filter) => oldFilter !== filter)},
      },
      true
    );
  };

  definitionConfig = () => {
    const {processDefinitionKey, processDefinitionVersions, tenantIds} = this.props;
    return {
      processDefinitionKey,
      processDefinitionVersions,
      tenantIds,
    };
  };

  processDefinitionIsNotSelected = () => {
    return (
      !this.props.processDefinitionKey ||
      this.props.processDefinitionKey === '' ||
      !this.props.processDefinitionVersions ||
      this.props.processDefinitionVersions.length === 0
    );
  };

  filterByInstancesOnly = (type) => () => {
    this.addFilter({
      type,
      data: null,
      filterLevel: this.props.filterLevel,
    });
  };

  render() {
    const FilterModal = this.getFilterModal(this.state.newFilterType);
    const EditFilterModal = this.getFilterModal(
      this.state.editFilter ? this.state.editFilter.type : null
    );

    const filters = this.props.data.filter(
      ({filterLevel}) => filterLevel === this.props.filterLevel
    );

    const FilterOptions = this.props.filterLevel === 'instance' ? InstanceFilters : ViewFilters;

    return (
      <div className="Filter">
        <div className="filterHeader">
          <Icon type="filter" />
          <span className="dropdownLabel">
            {t('common.filter.dropdownLabel.' + this.props.filterLevel)}
          </span>
          <FilterOptions
            processDefinitionIsNotSelected={this.processDefinitionIsNotSelected()}
            openNewFilterModal={this.openNewFilterModal}
            filterByInstancesOnly={this.filterByInstancesOnly}
          />
        </div>
        {filters.length === 0 && (
          <p className="emptyMessage">{t('common.filter.allVisible.' + this.props.filterLevel)}</p>
        )}
        {filters.length > 1 && <p className="linkingTip">{t('common.filter.linkingTip')}</p>}
        <FilterList
          {...this.definitionConfig()}
          flowNodeNames={this.props.flowNodeNames}
          openEditFilterModal={this.openEditFilterModal}
          data={filters}
          deleteFilter={this.deleteFilter}
        />
        <FilterModal
          addFilter={this.addFilter}
          close={this.closeModal}
          xml={this.props.xml}
          {...this.definitionConfig()}
          filterType={this.state.newFilterType}
          config={this.getFilterConfig(this.state.newFilterType)}
        />
        <EditFilterModal
          addFilter={this.editFilter}
          filterData={this.state.editFilter}
          close={this.closeModal}
          xml={this.props.xml}
          {...this.definitionConfig()}
          filterType={this.state.editFilter && this.state.editFilter.type}
          config={this.getFilterConfig(this.state.editFilter && this.state.editFilter.type)}
        />
      </div>
    );
  }
}
