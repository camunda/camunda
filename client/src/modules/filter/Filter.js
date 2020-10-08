/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Dropdown} from 'components';

import {
  DateFilter,
  VariableFilter,
  AssigneeFilter,
  NodeFilter,
  DurationFilter,
  NodeDuration,
} from './modals';

import FilterList from './FilterList';
import './Filter.scss';

import {loadValues, filterIncompatibleExistingFilters} from './service';
import {loadVariables} from 'services';
import {t} from 'translation';

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

  filterByInstancesOnly = (type) => (evt) => {
    this.addFilter({
      type,
      data: null,
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
        <Dropdown
          label={t('common.filter.addFilter')}
          id="ControlPanel__filters"
          className="Filter__dropdown"
        >
          <Dropdown.Submenu label={t('common.filter.types.instanceState')}>
            <Dropdown.Option onClick={this.filterByInstancesOnly('runningInstancesOnly')}>
              {t('common.filter.types.runningInstancesOnly')}
            </Dropdown.Option>
            <Dropdown.Option onClick={this.filterByInstancesOnly('completedInstancesOnly')}>
              {t('common.filter.types.completedInstancesOnly')}
            </Dropdown.Option>
            <Dropdown.Option onClick={this.filterByInstancesOnly('canceledInstancesOnly')}>
              {t('common.filter.types.canceledInstancesOnly')}
            </Dropdown.Option>
            <Dropdown.Option onClick={this.filterByInstancesOnly('nonCanceledInstancesOnly')}>
              {t('common.filter.types.nonCanceledInstancesOnly')}
            </Dropdown.Option>
            <Dropdown.Option onClick={this.filterByInstancesOnly('suspendedInstancesOnly')}>
              {t('common.filter.types.suspendedInstancesOnly')}
            </Dropdown.Option>
            <Dropdown.Option onClick={this.filterByInstancesOnly('nonSuspendedInstancesOnly')}>
              {t('common.filter.types.nonSuspendedInstancesOnly')}
            </Dropdown.Option>
          </Dropdown.Submenu>
          <Dropdown.Submenu label={t('common.filter.types.date')}>
            <Dropdown.Option onClick={this.openNewFilterModal('startDate')}>
              {t('common.filter.types.startDate')}
            </Dropdown.Option>
            <Dropdown.Option onClick={this.openNewFilterModal('endDate')}>
              {t('common.filter.types.endDate')}
            </Dropdown.Option>
          </Dropdown.Submenu>
          <Dropdown.Submenu label={t('common.filter.types.duration')}>
            <Dropdown.Option onClick={this.openNewFilterModal('processInstanceDuration')}>
              {t('common.filter.types.instance')}
            </Dropdown.Option>
            <Dropdown.Option
              disabled={this.processDefinitionIsNotSelected()}
              onClick={this.openNewFilterModal('flowNodeDuration')}
            >
              {t('common.filter.types.flowNode')}
            </Dropdown.Option>
          </Dropdown.Submenu>
          <Dropdown.Option
            disabled={this.processDefinitionIsNotSelected()}
            onClick={this.openNewFilterModal('variable')}
          >
            {t('common.filter.types.variable')}
          </Dropdown.Option>
          <Dropdown.Option
            disabled={this.processDefinitionIsNotSelected()}
            onClick={this.openNewFilterModal('executedFlowNodes')}
          >
            {t('common.filter.types.flowNode')}
          </Dropdown.Option>
          <Dropdown.Option
            disabled={this.processDefinitionIsNotSelected()}
            onClick={this.openNewFilterModal('assignee')}
          >
            {t('report.groupBy.userAssignee')}
          </Dropdown.Option>
          <Dropdown.Option
            disabled={this.processDefinitionIsNotSelected()}
            onClick={this.openNewFilterModal('candidateGroup')}
          >
            {t('report.groupBy.userGroup')}
          </Dropdown.Option>
        </Dropdown>
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
