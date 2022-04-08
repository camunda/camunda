/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {Icon, Tooltip} from 'components';
import {loadVariables} from 'services';
import {t} from 'translation';

import {
  DateFilter,
  MultipleVariableFilter,
  AssigneeFilter,
  NodeFilter,
  DurationFilter,
  NodeDuration,
  StateFilter,
  NodeSelection,
  NodeDateFilter,
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
      case 'instanceState':
      case 'incident':
      case 'incidentInstances':
      case 'flowNodeStatus':
        return StateFilter;
      case 'instanceStartDate':
      case 'instanceEndDate':
        return DateFilter;
      case 'flowNodeStartDate':
      case 'flowNodeEndDate':
        return NodeDateFilter;
      case 'multipleVariable':
        return MultipleVariableFilter;
      case 'processInstanceDuration':
        return DurationFilter;
      case 'flowNodeDuration':
        return NodeDuration;
      case 'executedFlowNodes':
      case 'executingFlowNodes':
      case 'canceledFlowNodes':
        const {newFilterLevel, editFilter} = this.state;
        if (newFilterLevel === 'view' || editFilter?.filterLevel === 'view') {
          return NodeSelection;
        }
        return NodeFilter;
      case 'assignee':
      case 'candidateGroup':
        return AssigneeFilter;
      default:
        return () => null;
    }
  };

  getFilterConfig = (type) => {
    if (type === 'multipleVariable') {
      return {
        getVariables: async (definition) =>
          await loadVariables([
            {
              processDefinitionKey: definition.key,
              processDefinitionVersions: definition.versions,
              tenantIds: definition.tenantIds,
            },
          ]),
        getValues: async (name, type, numResults, valueFilter, definition) =>
          await loadValues(
            definition.key,
            definition.versions,
            definition.tenantIds,
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

  processDefinitionIsNotSelected = () => {
    const {definitions} = this.props;

    return (
      !definitions?.length || // no definitions are selected
      definitions.every((definition) => !definition.versions.length || !definition.tenantIds.length) // every definition is missing either version or tenant selection
    );
  };

  render() {
    const {filterLevel, data, definitions} = this.props;
    const {newFilterType, editFilter, newFilterLevel} = this.state;

    const FilterModal = this.getFilterModal(newFilterType);
    const EditFilterModal = this.getFilterModal(editFilter ? editFilter.type : null);

    const displayAllFilters = !filterLevel;
    const displayInstanceFilters = displayAllFilters || filterLevel === 'instance';
    const displayViewFilters = displayAllFilters || filterLevel === 'view';

    const filters = data.filter(
      ({filterLevel}) => filterLevel === this.props.filterLevel || displayAllFilters
    );

    const modalFilterLevel = newFilterLevel || editFilter?.filterLevel;

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
          definitions={definitions}
          openEditFilterModal={this.openEditFilterModal}
          data={filters}
          deleteFilter={this.deleteFilter}
          variables={this.props.variables}
        />
        <FilterModal
          definitions={definitions}
          addFilter={this.addFilter}
          close={this.closeModal}
          filterType={newFilterType}
          config={this.getFilterConfig(newFilterType)}
          filterLevel={modalFilterLevel}
        />
        <EditFilterModal
          definitions={definitions}
          addFilter={this.editFilter}
          filterData={editFilter}
          close={this.closeModal}
          filterType={editFilter?.type}
          config={this.getFilterConfig(editFilter?.type)}
          filterLevel={modalFilterLevel}
        />
      </div>
    );
  }
}
