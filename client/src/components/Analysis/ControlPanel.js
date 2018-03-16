import React from 'react';
import {ActionItem, Popover, ProcessDefinitionSelection} from 'components';

import {extractProcessDefinitionName} from 'services';

import {Filter} from '../Reports';

import './ControlPanel.css';

export default class ControlPanel extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      processDefinitionName: this.props.processDefinitionKey
    };

    this.loadProcessDefinitionName(props.xml);
  }

  loadProcessDefinitionName = async xml => {
    if (xml) {
      const processDefinitionName = await extractProcessDefinitionName(xml);
      this.setState({
        processDefinitionName
      });
    }
  };

  componentWillReceiveProps(nextProps) {
    if (this.props.xml !== nextProps.xml) {
      this.loadProcessDefinitionName(nextProps.xml);
    }
  }

  getDefinitionConfig = () => {
    return {
      processDefinitionKey: this.props.processDefinitionKey,
      processDefinitionVersion: this.props.processDefinitionVersion
    };
  };

  hover = element => () => {
    this.props.updateHover(element);
  };

  createTitle = () => {
    const {processDefinitionKey, processDefinitionVersion} = this.props;
    const processDefintionName = this.state.processDefinitionName
      ? this.state.processDefinitionName
      : processDefinitionKey;
    if (processDefintionName && processDefinitionVersion) {
      return `${processDefintionName} : ${processDefinitionVersion}`;
    } else {
      return 'Select Process Definition';
    }
  };

  isProcDefSelected = () => {
    return !(!this.props.processDefinitionKey || !this.props.processDefinitionVersion);
  };

  render() {
    const {hoveredControl, hoveredNode} = this.props;
    const disableFlowNodeSelection = !this.isProcDefSelected();

    return (
      <div className="ControlPanel">
        <ul className="ControlPanel__list">
          <li className="ControlPanel__item ControlPanel__item--select">
            <label htmlFor="ControlPanel__process-definition" className="ControlPanel__label">
              Process definition
            </label>
            <Popover className="ControlPanel__popover" title={this.createTitle()}>
              <ProcessDefinitionSelection
                {...this.getDefinitionConfig()}
                xml={this.props.xml}
                onChange={this.props.onChange}
              />
            </Popover>
          </li>
          {[
            {type: 'endEvent', label: 'End Event', bpmnKey: 'bpmn:EndEvent'},
            {type: 'gateway', label: 'Gateway', bpmnKey: 'bpmn:Gateway'}
          ].map(({type, label, bpmnKey}) => (
            <li key={type} className="ControlPanel__item">
              <label htmlFor={'ControlPanel__' + type} className="ControlPanel__label">
                {label}
              </label>
              <div
                className={
                  'ControlPanel__config' +
                  (!disableFlowNodeSelection &&
                  (hoveredControl === type || (hoveredNode && hoveredNode.$instanceOf(bpmnKey)))
                    ? ' ControlPanel__config--hover'
                    : '')
                }
                name={'ControlPanel__' + type}
                onMouseOver={this.hover(type)}
                onMouseOut={this.hover(null)}
              >
                <ActionItem
                  disabled={disableFlowNodeSelection}
                  onClick={() => this.props.updateSelection(type, null)}
                >
                  {this.props[type]
                    ? this.props[type].name || this.props[type].id
                    : 'Please Select ' + label}
                </ActionItem>
              </div>
            </li>
          ))}
          <li className="ControlPanel__item ControlPanel__item--filter">
            <Filter
              data={this.props.filter}
              onChange={this.props.onChange}
              {...this.getDefinitionConfig()}
            />
          </li>
        </ul>
      </div>
    );
  }
}
