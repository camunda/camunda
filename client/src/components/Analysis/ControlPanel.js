import React from 'react';
import {ActionItem, Popover, ProcessDefinitionSelection} from 'components';

import {Filter} from '../Reports';

import './ControlPanel.css';

const ControlPanel = (props) => {

  const definitionConfig = {
    processDefinitionKey: props.processDefinitionKey,
    processDefinitionVersion: props.processDefinitionVersion
  }

  const hover = element => () => {
    props.updateHover(element);
  }

  const createTitle = () => {
    const {processDefinitionKey, processDefinitionVersion} = props;
    if(processDefinitionKey && processDefinitionVersion) {
      return `${processDefinitionKey} : ${processDefinitionVersion}`;
    } else {
      return 'Select Process Definition';
    }
  }

  const {hoveredControl, hoveredNode} = props;

  return (<div className='ControlPanel'>
      <ul className='ControlPanel__list'>
        <li className='ControlPanel__item ControlPanel__item--select'>
          <label htmlFor='ControlPanel__process-definition' className='ControlPanel__label'>Process definition</label>
          <Popover className='ControlPanel__popover' title={createTitle()}>
            <ProcessDefinitionSelection {...definitionConfig} xml={props.xml} onChange={props.onChange} />
          </Popover>
        </li>
        {[
        {type: 'endEvent', label: 'End Event', bpmnKey:'bpmn:EndEvent'},
        {type: 'gateway', label: 'Gateway', bpmnKey:'bpmn:Gateway'}].map(({type, label, bpmnKey}) =>
          <li key={type} className='ControlPanel__item'>
            <label htmlFor={'ControlPanel__' + type} className='ControlPanel__label'>{label}</label>
            <div
              className={'ControlPanel__config' + (hoveredControl === type || (hoveredNode && hoveredNode.$instanceOf(bpmnKey)) ? ' ControlPanel__config--hover' : '')}
              name={'ControlPanel__' + type}
              onMouseOver={hover(type)}
              onMouseOut={hover(null)}
              >
                <ActionItem onClick={() => props.updateSelection(type, null)}>
                  {props[type] ?
                    props[type].name || props[type].id
                  : 'Please Select ' + label}
                </ActionItem>
            </div>
          </li>
        )}
        <li className='ControlPanel__item ControlPanel__item--filter'>
          <Filter data={props.filter} onChange={props.onChange} {...definitionConfig} />
        </li>
      </ul>
    </div>
  );

};

export default ControlPanel;
