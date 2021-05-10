/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import update from 'immutability-helper';
import React from 'react';
import equal from 'fast-deep-equal';

import {DefinitionSelection, SelectionPreview} from 'components';
import {Filter} from 'filter';
import {getFlowNodeNames, loadVariables} from 'services';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import './BranchControlPanel.scss';

export class BranchControlPanel extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      flowNodeNames: null,
      variables: null,
    };
  }

  componentDidMount() {
    this.loadFlowNodeNames();
    this.loadVariables();
  }

  loadVariables = () => {
    const {processDefinitionKey, processDefinitionVersions, tenantIds} = this.props;
    if (processDefinitionKey && processDefinitionVersions) {
      this.props.mightFail(
        loadVariables([{processDefinitionKey, processDefinitionVersions, tenantIds}]),
        (variables) => this.setState({variables}),
        showError
      );
    }
  };

  loadFlowNodeNames = async () => {
    this.setState({
      flowNodeNames: await getFlowNodeNames(
        this.props.processDefinitionKey,
        this.props.processDefinitionVersions[0],
        this.props.tenantIds[0]
      ),
    });
  };

  componentDidUpdate({processDefinitionKey, processDefinitionVersions}) {
    if (
      this.props.processDefinitionKey !== processDefinitionKey ||
      !equal(this.props.processDefinitionVersions, processDefinitionVersions)
    ) {
      this.loadFlowNodeNames();
      this.loadVariables();
    }
  }

  getDefinitionConfig = () => {
    return {
      processDefinitionKey: this.props.processDefinitionKey,
      processDefinitionVersions: this.props.processDefinitionVersions,
      tenantIds: this.props.tenantIds,
    };
  };

  hover = (element) => () => {
    this.props.updateHover(element);
  };

  isProcDefSelected = () => {
    return this.props.processDefinitionKey && this.props.processDefinitionVersions;
  };

  renderInput = ({type, bpmnKey}) => {
    const {hoveredControl, hoveredNode} = this.props;
    const disableFlowNodeSelection = !this.isProcDefSelected();

    return (
      <div
        className="config"
        name={type}
        onMouseOver={this.hover(type)}
        onMouseOut={this.hover(null)}
      >
        <SelectionPreview
          disabled={disableFlowNodeSelection}
          onClick={() => this.props.updateSelection(type, null)}
          highlighted={
            !disableFlowNodeSelection &&
            (hoveredControl === type || (hoveredNode && hoveredNode.$instanceOf(bpmnKey)))
          }
        >
          {this.props[type]
            ? this.props[type].name || this.props[type].id
            : t(`analysis.emptySelectionLabel.${type}`)}
        </SelectionPreview>
      </div>
    );
  };

  render() {
    return (
      <div className="BranchControlPanel">
        <ul className="list">
          <li className="item summary">
            {t('analysis.selectLabel')}
            <DefinitionSelection
              type="process"
              definitionKey={this.props.processDefinitionKey}
              versions={this.props.processDefinitionVersions}
              tenants={this.props.tenantIds}
              xml={this.props.xml}
              onChange={({key, versions, tenantIds}) =>
                this.props.onChange({
                  processDefinitionKey: key,
                  processDefinitionVersions: versions,
                  tenantIds,
                })
              }
            />
            {t('analysis.gatewayLabel')}
            {this.renderInput({type: 'gateway', bpmnKey: 'bpmn:Gateway'})}
            {t('analysis.endEventLabel')}
            {this.renderInput({type: 'endEvent', bpmnKey: 'bpmn:EndEvent'})}
          </li>
          <li className="item itemFilter">
            <Filter
              data={this.props.filter}
              onChange={({filter}) =>
                this.props.onChange({filter: update(this.props.filter, filter)})
              }
              xml={this.props.xml}
              {...this.getDefinitionConfig()}
              filterLevel="instance"
              flowNodeNames={this.state.flowNodeNames}
              variables={this.state.variables}
            />
          </li>
        </ul>
      </div>
    );
  }
}

export default withErrorHandling(BranchControlPanel);
