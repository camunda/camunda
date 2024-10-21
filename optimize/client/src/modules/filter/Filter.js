/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Component} from 'react';
import {Tooltip} from '@carbon/react';
import {Information} from '@carbon/icons-react';

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

export default class Filter extends Component {
  state = {
    newFilterType: null,
    newFilterLevel: null,
    editFilter: null,
  };

  openNewFilterModal = (filterLevel) => (type) => () => {
    this.setState({newFilterType: type, newFilterLevel: filterLevel});
  };

  openEditFilterModal = (filter) => () => {
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
      case 'executedFlowNodes': {
        const {newFilterLevel, editFilter} = this.state;
        if (newFilterLevel === 'view' || editFilter?.filterLevel === 'view') {
          return NodeSelection;
        }
        return NodeFilter;
      }
      case 'executingFlowNodes':
      case 'canceledFlowNodes':
        return NodeFilter;
      case 'assignee':
        return AssigneeFilter;
      default:
        return () => null;
    }
  };

  getFilterConfig = (type) => {
    if (type === 'multipleVariable') {
      return {
        getVariables: async (definition) =>
          await loadVariables({
            processesToQuery: [
              {
                processDefinitionKey: definition.key,
                processDefinitionVersions: definition.versions,
                tenantIds: definition.tenantIds,
              },
            ],
            filter: this.props.data,
          }),
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
              <Tooltip label={t('common.filter.tooltip.instance')}>
                <button className="tooltipTriggerBtn">
                  <Information />
                </button>
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
              <Tooltip className="flowNodeFilterTooltip" label={t('common.filter.tooltip.view')}>
                <button className="tooltipTriggerBtn">
                  <Information />
                </button>
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
