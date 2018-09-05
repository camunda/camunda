import React from 'react';

import {Dropdown} from 'components';

import {DateFilter, VariableFilter, NodeFilter, DurationFilter} from './modals';

import FilterList from './FilterList';
import './Filter.css';

export default class Filter extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      newFilterType: null,
      editFilter: null
    };
  }

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

  editFilter = newFilter => {
    const filters = [...this.props.data];

    const index = filters.indexOf(filters.find(v => this.state.editFilter.data === v.data));

    filters.splice(index, 1, newFilter);

    this.props.onChange({filter: filters});
    this.closeModal();
  };

  addFilter = newFilter => {
    let filters = this.props.data;
    filters = this.filterIncompatibleExistingFilters(filters, newFilter.type, ['startDate']);
    filters = this.filterIncompatibleExistingFilters(filters, newFilter.type, [
      'runningInstancesOnly',
      'completedInstancesOnly'
    ]);

    filters = this.filterIncompatibleExistingFilters(filters, newFilter.type, [
      'runningInstancesOnly',
      'canceledInstancesOnly'
    ]);

    this.props.onChange({filter: [...filters, newFilter]});
    this.closeModal();
  };

  filterIncompatibleExistingFilters = (filters, newFilterType, uniqueTypes) => {
    if (uniqueTypes.includes(newFilterType)) {
      return filters.filter(({type}) => !uniqueTypes.includes(type));
    }
    return filters;
  };

  deleteFilter = oldFilter => {
    this.props.onChange({
      filter: this.props.data.filter(filter => oldFilter !== filter)
    });
  };

  definitionConfig = () => {
    return {
      processDefinitionKey: this.props.processDefinitionKey,
      processDefinitionVersion: this.props.processDefinitionVersion
    };
  };

  processDefinitionIsNotSelected = () => {
    return (
      !this.props.processDefinitionKey ||
      this.props.processDefinitionKey === '' ||
      !this.props.processDefinitionVersion ||
      this.props.processDefinitionVersion === ''
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
        <label htmlFor="ControlPanel__filters" className="visually-hidden">
          Filters
        </label>
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
          <Dropdown.Option onClick={this.openNewFilterModal('startDate')}>
            Start Date
          </Dropdown.Option>
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
        <FilterModal
          addFilter={this.addFilter}
          close={this.closeModal}
          xml={this.props.xml}
          {...this.definitionConfig()}
        />
        <EditFilterModal
          addFilter={this.editFilter}
          filterData={this.state.editFilter}
          close={this.closeModal}
          xml={this.props.xml}
          {...this.definitionConfig()}
        />
      </div>
    );
  }
}
