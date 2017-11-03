import React from 'react';

import {loadProcessDefinitions} from './service';

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
    this.props.onChange('view', {operation: data[0], entity: data[1]});
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
    return <div>
      ProcessDefinition:
      <select value={this.props.processDefinitionId} onChange={this.changeDefinition}>
        <option value='' disabled>Please select process instance</option>
        {this.state.availableDefinitions.map(definition => <option value={definition.id} key={definition.id}>{definition.id}</option>)}
      </select>
      View:
      <select value={parseView(this.props.view)} onChange={this.changeView}>
        <option value='count_processInstance'>Count Process Instance Frequency</option>
        <option value='count_flowNode'>Count Flow Node Frequency</option>
        <option value='avg_processInstance'>Average Process Instance Duration</option>
        <option value='avg_flowNode'>Average Flow Node Duration</option>
      </select>
      Group By:
      <select value={parseGroup(this.props.groupBy)} onChange={this.changeGroup}>
        <option value='none_null'>None</option>
        <option value='flowNodes_null'>Flow Nodes</option>
        <option value='startDate_year'>Start Date of Process Instance - Year</option>
        <option value='startDate_month'>Start Date of Process Instance - Month</option>
        <option value='startDate_week'>Start Date of Process Instance - Week</option>
        <option value='startDate_day'>Start Date of Process Instance - Day</option>
        <option value='startDate_hour'>Start Date of Process Instance - Hour</option>
      </select>
      Visualize as:
      <select value={this.props.visualization} onChange={this.changeVisualization}>
        <option value='number'>Number</option>
        <option value='table'>Table</option>
        <option value='bar'>Bar Chart</option>
        <option value='line'>Line Chart</option>
        <option value='pie'>Pie Chart</option>
        <option value='badge'>Diagram Badge</option>
        <option value='heat'>Heatmap</option>
        <option value='json'>JSON Dump</option>
      </select>
    </div>
  }
}

function parseView({operation, entity}) {
  return `${operation}_${entity}`;
}
function parseGroup({type, unit}) {
  return `${type}_${unit}`;
}
