import React from 'react';

import {Dropdown} from 'components';

import {DateFilter, VariableFilter, NodeFilter} from './modals';

export default class Filter extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      newFilterType: null
    }
  }

  openNewFilterModal = type => evt => {
    this.setState({newFilterType: type});
  }

  closeModal = () => {
    this.setState({newFilterType: null});
  }

  getFilterModal = () => {
    switch(this.state.newFilterType) {
      case 'date': return DateFilter;
      case 'variable': return VariableFilter;
      case 'node': return NodeFilter;
      default: return () => null;
    }
  }

  addFilter = ({type, data}) => {
    const newFilter = (this.props.data && {...this.props.data}) || {};

    switch(type) {
      case 'date':
        newFilter.dates = [{
          type: 'start_date',
          operator: '>=',
          value: data.start
        }, {
          type: 'start_date',
          operator: '<=',
          value: data.end
        }];
        break;
      default: return;
    }

    this.props.onChange('filter', newFilter);

    this.closeModal();
  }

  render() {
    const FilterModal = this.getFilterModal();

    return (<div style={{display: 'inline-block'}}>
      <Dropdown label='Add Filter'>
        <Dropdown.Option onClick={this.openNewFilterModal('date')}>Start Date</Dropdown.Option>
        <Dropdown.Option onClick={this.openNewFilterModal('variable')}>Variable</Dropdown.Option>
        <Dropdown.Option onClick={this.openNewFilterModal('node')}>Flow Node</Dropdown.Option>
      </Dropdown>
      <FilterModal addFilter={this.addFilter} close={this.closeModal} />
    </div>);
  }
}
