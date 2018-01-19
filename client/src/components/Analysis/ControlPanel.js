import React from 'react';
import {Select, ActionItem} from 'components';

import {Filter} from '../Reports';
import {loadProcessDefinitions} from './service';

import './ControlPanel.css';

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

  hover = element => () => {
    this.props.updateHover(element);
  }

  render() {
    const {hoveredControl, hoveredNode} = this.props;

    return <div className='ControlPanel'>
      <ul className='ControlPanel__list'>
        <li className='ControlPanel__item ControlPanel__item--select'>
          <label htmlFor='ControlPanel__process-definition' className='ControlPanel__label'>Process definition</label>
          <Select className='ControlPanel__select' name='ControlPanel__process-definition' value={this.props.processDefinitionId} onChange={this.changeDefinition}>
            {addSelectionOption()}
            {this.state.availableDefinitions.map(definition => <Select.Option value={definition.id} key={definition.id}>{definition.id}</Select.Option>)}
          </Select>
        </li>
        {[
        {type: 'endEvent', label: 'End Event', bpmnKey:'bpmn:EndEvent'},
        {type: 'gateway', label: 'Gateway', bpmnKey:'bpmn:Gateway'}].map(({type, label, bpmnKey}) =>
          <li key={type} className='ControlPanel__item'>
            <label htmlFor={'ControlPanel__' + type} className='ControlPanel__label'>{label}</label>
            <div
              className={'ControlPanel__config' + (hoveredControl === type || (hoveredNode && hoveredNode.$instanceOf(bpmnKey)) ? ' ControlPanel__config--hover' : '')}
              name={'ControlPanel__' + type}
              onMouseOver={this.hover(type)}
              onMouseOut={this.hover(null)}
              >
                <ActionItem onClick={() => this.props.updateSelection(type, null)}>
                  {this.props[type] ?
                    this.props[type].name || this.props[type].id
                  : 'Please Select ' + label}
                </ActionItem>
            </div>
          </li>
        )}
        <li className='ControlPanel__item ControlPanel__item--filter'>
          <Filter data={this.props.filter} onChange={this.props.onChange} processDefinitionId={this.props.processDefinitionId} />
        </li>
      </ul>
    </div>
  }
}

function addSelectionOption() {
  return <Select.Option value=''>Please select...</Select.Option>;
}
