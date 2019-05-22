/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import classnames from 'classnames';
import update from 'immutability-helper';
import React from 'react';
import {ActionItem, DefinitionSelection} from 'components';

import {extractDefinitionName, getFlowNodeNames} from 'services';

import {Filter} from '../Reports';

import './AnalysisControlPanel.scss';

export default class AnalysisControlPanel extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      processDefinitionName: this.props.processDefinitionKey,
      flowNodeNames: null
    };
  }

  componentDidMount() {
    this.loadProcessDefinitionName();
    this.loadFlowNodeNames();
  }

  loadProcessDefinitionName = () => {
    const {xml, processDefinitionKey} = this.props;
    if (xml) {
      const processDefinitionName = extractDefinitionName(processDefinitionKey, xml);
      this.setState({
        processDefinitionName
      });
    }
  };

  loadFlowNodeNames = async () => {
    this.setState({
      flowNodeNames: await getFlowNodeNames(
        this.props.processDefinitionKey,
        this.props.processDefinitionVersion
      )
    });
  };

  componentDidUpdate({xml, processDefinitionKey, processDefinitionVersion}) {
    if (this.props.xml !== xml) {
      this.loadProcessDefinitionName();
    }

    if (
      this.props.processDefinitionKey !== processDefinitionKey ||
      this.props.processDefinitionVersion !== processDefinitionVersion
    ) {
      this.loadFlowNodeNames();
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

  isProcDefSelected = () => {
    return this.props.processDefinitionKey && this.props.processDefinitionVersion;
  };

  renderInput = ({type, label, bpmnKey}) => {
    const {hoveredControl, hoveredNode} = this.props;
    const disableFlowNodeSelection = !this.isProcDefSelected();

    return (
      <div
        className={classnames('AnalysisControlPanel__config', {
          'AnalysisControlPanel__config--hover':
            !disableFlowNodeSelection &&
            (hoveredControl === type || (hoveredNode && hoveredNode.$instanceOf(bpmnKey)))
        })}
        name={'AnalysisControlPanel__' + type}
        onMouseOver={this.hover(type)}
        onMouseOut={this.hover(null)}
      >
        <ActionItem
          disabled={disableFlowNodeSelection}
          onClick={() => this.props.updateSelection(type, null)}
        >
          {this.props[type] ? this.props[type].name || this.props[type].id : 'Select ' + label}
        </ActionItem>
      </div>
    );
  };

  render() {
    return (
      <div className="AnalysisControlPanel">
        <ul className="AnalysisControlPanel__list">
          <li className="AnalysisControlPanel__item summary">
            For
            <DefinitionSelection
              type="process"
              definitionKey={this.props.processDefinitionKey}
              definitionVersion={this.props.processDefinitionVersion}
              tenants={this.props.tenantIds}
              xml={this.props.xml}
              onChange={(key, version, tenantIds) =>
                this.props.onChange({
                  processDefinitionKey: key,
                  processDefinitionVersion: version,
                  tenantIds,
                  filter: this.props.filter.filter(
                    ({type}) => type !== 'executedFlowNodes' && type !== 'variable'
                  )
                })
              }
            />
            analyse how the branches of
            {this.renderInput({type: 'gateway', label: 'Gateway', bpmnKey: 'bpmn:Gateway'})}
            affect the probability that an instance reached
            {this.renderInput({type: 'endEvent', label: 'End Event', bpmnKey: 'bpmn:EndEvent'})}
          </li>
          <li className="AnalysisControlPanel__item AnalysisControlPanel__item--filter">
            <Filter
              data={this.props.filter}
              flowNodeNames={this.state.flowNodeNames}
              onChange={({filter}) =>
                this.props.onChange({filter: update(this.props.filter, filter)})
              }
              xml={this.props.xml}
              {...this.getDefinitionConfig()}
            />
          </li>
        </ul>
      </div>
    );
  }
}
