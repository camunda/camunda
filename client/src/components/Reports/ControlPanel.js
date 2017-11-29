import React from 'react';

import {Filter} from './filter';
import {loadProcessDefinitions} from './service';

import './ControlPanel.css';

const options = {
  view: [
    {key: 'rawData_ignored_ignored', label: 'Raw Data'},
    {key: 'count_processInstance_frequency', label: 'Count Process Instance Frequency'},
    {key: 'count_flowNode_frequency', label: 'Count Flow Node Frequency'},
    {key: 'avg_processInstance_duration', label: 'Average Process Instance Duration'},
    {key: 'avg_flowNode_duration', label: 'Average Flow Node Duration'}
    
  ],
  groupBy: [
    {key: 'none_null', label: 'None'},
    {key: 'flowNodes_null', label: 'Flow Nodes'},
    {key: 'startDate_year', label: 'Start Date of Process Instance - Year'},
    {key: 'startDate_month', label: 'Start Date of Process Instance - Month'},
    {key: 'startDate_week', label: 'Start Date of Process Instance - Week'},
    {key: 'startDate_day', label: 'Start Date of Process Instance - Day'},
    {key: 'startDate_hour', label: 'Start Date of Process Instance - Hour'}
  ],
  visualizeAs: [
    {key: 'number', label: 'Number'},
    {key: 'table', label: 'Table'},
    {key: 'bar', label: 'Bar Chart'},
    {key: 'line', label: 'Line Chart'},
    {key: 'pie', label: 'Pie Chart'},
    {key: 'badge', label: 'Diagram Badge'},
    {key: 'heat', label: 'Heatmap'},
    {key: 'json', label: 'JSON Dump'}
  ]
};

export default class ControlPanel extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      availableDefinitions: [],
      loaded: false
    };

    this.loadAvailableDefinitions();
  }

  loadAvailableDefinitions = async () => {
    this.setState({
      availableDefinitions: await loadProcessDefinitions(),
      loaded: true
    });
  }

  changeDefinition = evt => {
    this.props.onChange('processDefinitionId', evt.target.value);
  }
  changeView = evt => {
    const data = evt.target.value.split('_');
    this.props.onChange('view', {operation: data[0], entity: data[1], property: data[2]});
  }
  changeGroup = evt => {
    const data = evt.target.value.split('_');

    const type = data[0];
    const unit = data[1] === 'null' ? null : data[1];

    this.props.onChange('groupBy', {type, unit});
  }
  changeVisualization = evt => {
    this.props.onChange('visualization', evt.target.value);
  }

  render() {
    return <div className='ControlPanel'>
      <ul className='ControlPanel__list'>
        <li className='ControlPanel__item'>
          <label htmlFor='process-definition' className='ControlPanel__label'>ProcessDefinition</label>
          <select name='process-definition' className='Select' value={this.props.processDefinitionId} onChange={this.changeDefinition}>
            <option value=''>Please select process instance</option>
            {this.state.availableDefinitions.map(definition => <option value={definition.id} key={definition.id}>{definition.id}</option>)}
          </select>
        </li>
        <li className='ControlPanel__item'>
          <label htmlFor='view' className='ControlPanel__label'>View</label>
          <select name='view' className='Select' value={parseView(this.props.view)} onChange={this.changeView}>
            <option value=''>Please select a value</option>
            {renderOptions('view')}
          </select>
        </li>
        <li className='ControlPanel__item'>
          <label htmlFor='group-by' className='ControlPanel__label'>Group By</label>
          <select name='group-by' className='Select' value={parseGroup(this.props.groupBy)} onChange={this.changeGroup}>
            <option value=''>Please select a value</option>
            {renderOptions('groupBy')}
          </select>
        </li>
        <li className='ControlPanel__item'>
          <label htmlFor='visualize-as' className='ControlPanel__label'>Visualize as</label>
          <select name='visualize-as' className='Select' value={this.props.visualization} onChange={this.changeVisualization}>
            <option value=''>Please select a value</option>
            {renderOptions('visualizeAs')}
          </select>
        </li>
        <li className='ControlPanel__item ControlPanel__item--filter'>
          <Filter data={this.props.filter} onChange={this.props.onChange} />
        </li>
      </ul>
    </div>
  }
}

function parseView({operation, entity, property}) {
  return `${operation}_${entity}_${property}`;
}
function parseGroup({type, unit}) {
  return `${type}_${unit}`;
}

function renderOptions(prop) {
  return options[prop].map(({key, label}) => <option key={key} value={key}>{label}</option>);
}
