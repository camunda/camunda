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
      editFilter: {}
    }
  }

  openNewFilterModal = type => evt => {
    this.setState({newFilterType: type});
  }

  openEditFilterModal = i => evt => {
    if(evt.target.className !== 'Button ActionItem__button ') {
      let filter;
      if (this.props.data[i].type === 'date') {
        filter = [this.props.data[i], this.props.data[i+1]]
      } else {
        filter = this.props.data[i];
      }
      this.setState({editFilter: filter});
    }
  }

  closeModal = () => {
    this.setState({newFilterType: null, editFilter: {}});
  }

  getFilterModal = () => {
    switch(this.state.newFilterType) {
      case 'date': return DateFilter;
      case 'variable': return VariableFilter;
      case 'duration': return DurationFilter;
      case 'node': return NodeFilter;
      default: return () => null;
    }
  }

  getEditFilterModal = () => {
    if(this.state.editFilter[0]) {
      return DateFilter;
    } else {
      switch(this.state.editFilter.type) {
        case 'rollingDate': return DateFilter;
        case 'variable': return VariableFilter;
        case 'processInstanceDuration': return DurationFilter;
        case 'executedFlowNodes': return NodeFilter;
        default: return () => null;
      }
    }
  }

  editFilter = (...newFilters) => {
    let filters = this.props.data;
    if (this.state.editFilter[0]) {
      this.addFilter(...newFilters);
    } else {
      filters = filters.filter((filter) => (filter !== this.state.editFilter))
      this.props.onChange('filter', [...filters, ...newFilters]);
      this.closeModal();
    }
  }

  addFilter = (...newFilters) => {
    let filters = this.props.data;

    if(newFilters[0].type === 'date' || newFilters[0].type === 'rollingDate') {
      filters = filters.filter(({type}) => type !== 'date' && type !== 'rollingDate');
    }

    this.props.onChange('filter', [...filters, ...newFilters]);
    this.closeModal();
  }

  deleteFilter = (...oldFilters) => {
    this.props.onChange('filter', [...this.props.data.filter(filter => !oldFilters.includes(filter))]);
  }

  processDefinitionIsNotSelected = () => {
    return !this.props.processDefinitionId ||
    this.props.processDefinitionId === '';
  }

  render() {
    const FilterModal = this.getFilterModal();
    const EditFilterModal = this.getEditFilterModal();

    return (<div className='Filter'>
      <label htmlFor='ControlPanel__filters' className='visually-hidden'>Filters</label>
      <FilterList openEditFilterModal={this.openEditFilterModal} data={this.props.data} deleteFilter={this.deleteFilter} />
      <Dropdown label='Add Filter' id='ControlPanel__filters' className='Filter__dropdown' >
        <Dropdown.Option onClick={this.openNewFilterModal('date')}>Start Date</Dropdown.Option>
        <Dropdown.Option onClick={this.openNewFilterModal('duration')}>Duration</Dropdown.Option>
        <Dropdown.Option disabled={this.processDefinitionIsNotSelected()} onClick={this.openNewFilterModal('variable')}>Variable</Dropdown.Option>
        <Dropdown.Option disabled={this.processDefinitionIsNotSelected()} onClick={this.openNewFilterModal('node')}>Flow Node</Dropdown.Option>
      </Dropdown>
      <FilterModal addFilter={this.addFilter} close={this.closeModal} processDefinitionId={this.props.processDefinitionId} />
      <EditFilterModal addFilter={this.editFilter} filterData={this.state.editFilter} openEditFilterModal={this.openEditFilterModal} close={this.closeModal} processDefinitionId={this.props.processDefinitionId}/>
    </div>);
  }
}
