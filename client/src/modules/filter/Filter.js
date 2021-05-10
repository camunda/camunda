/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Icon, Tooltip} from 'components';
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
import {loadValues, filterSameTypeExistingFilters} from './service';
import InstanceFilters from './InstanceFilters';
import ViewFilters from './ViewFilters';

import './Filter.scss';

export default class Filter extends React.Component {
  state = {
    newFilterType: null,
    newFilterLevel: null,
    editFilter: null,
  };

  openNewFilterModal = (filterLevel) => (type) => (evt) => {
    this.setState({newFilterType: type, newFilterLevel: filterLevel});
  };

  openEditFilterModal = (filter) => (evt) => {
    this.setState({editFilter: filter});
  };

  closeModal = () => {
    this.setState({newFilterType: null, newFilterLevel: null, editFilter: null});
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
          await loadVariables([{processDefinitionKey, processDefinitionVersions, tenantIds}]),
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

    newFilter.filterLevel = filters[index].filterLevel;
    this.props.onChange({filter: {[index]: {$set: newFilter}}}, true);
    this.closeModal();
  };

  addFilter = (newFilter) => {
    newFilter.filterLevel = newFilter.filterLevel || this.state.newFilterLevel;
    const filters = filterSameTypeExistingFilters(this.props.data, newFilter);
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

  filterByTypeOnly = (filterLevel) => (type) => () => {
    this.addFilter({
      type,
      data: null,
      filterLevel,
    });
  };

  render() {
    const FilterModal = this.getFilterModal(this.state.newFilterType);
    const EditFilterModal = this.getFilterModal(
      this.state.editFilter ? this.state.editFilter.type : null
    );

    const displayAllFilters = !this.props.filterLevel;
    const displayInstanceFilters = displayAllFilters || this.props.filterLevel === 'instance';
    const displayViewFilters = displayAllFilters || this.props.filterLevel === 'view';

    const filters = this.props.data.filter(
      ({filterLevel}) => filterLevel === this.props.filterLevel || displayAllFilters
    );

    return (
      <div className="Filter">
        {displayInstanceFilters && (
          <div className="filterHeader">
            <span className="dropdownLabel">{t('common.filter.dropdownLabel.instance')}</span>
            <div className="explanation">
              <Tooltip
                align="right"
                content={
                  <div className="filterLevelTooltip">{t('common.filter.tooltip.instance')}</div>
                }
              >
                <Icon type="info" />
              </Tooltip>
            </div>
            <InstanceFilters
              processDefinitionIsNotSelected={this.processDefinitionIsNotSelected()}
              openNewFilterModal={this.openNewFilterModal('instance')}
              filterByTypeOnly={this.filterByTypeOnly('instance')}
            />
          </div>
        )}
        {displayViewFilters && (
          <div className="filterHeader">
            <span className="dropdownLabel">{t('common.filter.dropdownLabel.view')}</span>
            <div className="explanation">
              <Tooltip
                align="right"
                content={
                  <div className="filterLevelTooltip">{t('common.filter.tooltip.view')}</div>
                }
              >
                <Icon type="info" />
              </Tooltip>
            </div>
            <ViewFilters
              processDefinitionIsNotSelected={this.processDefinitionIsNotSelected()}
              openNewFilterModal={this.openNewFilterModal('view')}
              filterByTypeOnly={this.filterByTypeOnly('view')}
            />
          </div>
        )}
        {filters.length === 0 && (
          <p className="emptyMessage">
            {t('common.filter.allVisible.' + (this.props.filterLevel ?? 'instance'))}
          </p>
        )}
        {filters.length > 1 && <p className="linkingTip">{t('common.filter.linkingTip')}</p>}
        <FilterList
          {...this.definitionConfig()}
          flowNodeNames={this.props.flowNodeNames}
          openEditFilterModal={this.openEditFilterModal}
          data={filters}
          deleteFilter={this.deleteFilter}
          variables={this.props.variables}
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
