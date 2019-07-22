/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Dropdown} from 'components';

import {DateFilter, VariableFilter, NodeFilter, DurationFilter} from './modals';

import FilterList from './FilterList';
import './Filter.scss';

import {loadValues, filterIncompatibleExistingFilters} from './service';
import {loadVariables} from '../service';

export default class Filter extends React.Component {
  state = {
    newFilterType: null,
    editFilter: null
  };

  openNewFilterModal = type => evt => {
    this.setState({newFilterType: type});
  };

  openEditFilterModal = filter => evt => {
    this.setState({editFilter: filter});
  };

  closeModal = () => {
    this.setState({newFilterType: null, editFilter: null});
  };

  getFilterModal = type => {
    switch (type) {
      case 'startDate':
      case 'endDate':
        return DateFilter;
      case 'variable':
        return VariableFilter;
      case 'processInstanceDuration':
        return DurationFilter;
      case 'executedFlowNodes':
        return NodeFilter;
      default:
        return () => null;
    }
  };

  getFilterConfig = type => {
    if (type === 'variable') {
      return {
        getVariables: async () =>
          await loadVariables(
            this.props.processDefinitionKey,
            this.props.processDefinitionVersions,
            this.props.tenantIds
          ),
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
          )
      };
    }
  };

  editFilter = newFilter => {
    const filters = this.props.data;

    const index = filters.indexOf(filters.find(v => this.state.editFilter.data === v.data));

    this.props.onChange({filter: {[index]: {$set: newFilter}}}, true);
    this.closeModal();
  };

  addFilter = newFilter => {
    let filters = this.props.data;
    filters = filterIncompatibleExistingFilters(filters, newFilter.type, ['startDate']);
    filters = filterIncompatibleExistingFilters(filters, newFilter.type, ['endDate']);
    filters = filterIncompatibleExistingFilters(filters, newFilter.type, [
      'completedInstancesOnly'
    ]);
    filters = filterIncompatibleExistingFilters(filters, newFilter.type, ['runningInstancesOnly']);
    filters = filterIncompatibleExistingFilters(filters, newFilter.type, ['canceledInstancesOnly']);

    this.props.onChange({filter: {$set: [...filters, newFilter]}}, true);
    this.closeModal();
  };

  deleteFilter = oldFilter => {
    this.props.onChange(
      {
        filter: {$set: this.props.data.filter(filter => oldFilter !== filter)}
      },
      true
    );
  };

  definitionConfig = () => {
    return {
      processDefinitionKey: this.props.processDefinitionKey,
      processDefinitionVersions: this.props.processDefinitionVersions
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

  filterByInstancesOnly = type => evt => {
    this.addFilter({
      type,
      data: null
    });
  };

  render() {
    const FilterModal = this.getFilterModal(this.state.newFilterType);
    const EditFilterModal = this.getFilterModal(
      this.state.editFilter ? this.state.editFilter.type : null
    );

    return (
      <div className="Filter">
        <FilterList
          {...this.definitionConfig()}
          flowNodeNames={this.props.flowNodeNames}
          openEditFilterModal={this.openEditFilterModal}
          data={this.props.data}
          deleteFilter={this.deleteFilter}
        />
        <Dropdown label="Add Filter" id="ControlPanel__filters" className="Filter__dropdown">
          <Dropdown.Option onClick={this.filterByInstancesOnly('runningInstancesOnly')}>
            Running Instances Only
          </Dropdown.Option>
          <Dropdown.Option onClick={this.filterByInstancesOnly('completedInstancesOnly')}>
            Completed Instances Only
          </Dropdown.Option>
          <Dropdown.Option onClick={this.filterByInstancesOnly('canceledInstancesOnly')}>
            Canceled Instances Only
          </Dropdown.Option>
          <Dropdown.Option onClick={this.filterByInstancesOnly('nonCanceledInstancesOnly')}>
            Non Canceled Instances Only
          </Dropdown.Option>
          <Dropdown.Option onClick={this.openNewFilterModal('startDate')}>
            Start Date
          </Dropdown.Option>
          <Dropdown.Option onClick={this.openNewFilterModal('endDate')}>End Date</Dropdown.Option>
          <Dropdown.Option onClick={this.openNewFilterModal('processInstanceDuration')}>
            Duration
          </Dropdown.Option>
          <Dropdown.Option
            disabled={this.processDefinitionIsNotSelected()}
            onClick={this.openNewFilterModal('variable')}
          >
            Variable
          </Dropdown.Option>
          <Dropdown.Option
            disabled={this.processDefinitionIsNotSelected()}
            onClick={this.openNewFilterModal('executedFlowNodes')}
          >
            Flow Node
          </Dropdown.Option>
        </Dropdown>
        {this.props.instanceCount !== undefined && (
          <span className="instanceCount">
            {this.props.instanceCount} instance{this.props.instanceCount !== 1 && 's'} in current
            filter
          </span>
        )}
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
