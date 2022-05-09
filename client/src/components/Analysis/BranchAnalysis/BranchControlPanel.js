/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import update from 'immutability-helper';
import React from 'react';
import equal from 'fast-deep-equal';

import {DefinitionSelection, SelectionPreview} from 'components';
import {Filter} from 'filter';
import {loadVariables} from 'services';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import './BranchControlPanel.scss';

export class BranchControlPanel extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      variables: null,
    };
  }

  componentDidMount() {
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

  componentDidUpdate({processDefinitionKey, processDefinitionVersions}) {
    if (
      this.props.processDefinitionKey !== processDefinitionKey ||
      !equal(this.props.processDefinitionVersions, processDefinitionVersions)
    ) {
      this.loadVariables();
    }
  }

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
    const {processDefinitionKey, processDefinitionVersions, tenantIds} = this.props;

    const definitions = [];
    if (processDefinitionKey) {
      definitions.push({
        identifier: 'definition',
        key: processDefinitionKey,
        versions: processDefinitionVersions,
        tenantIds: tenantIds,
        name: processDefinitionKey,
        displayName: processDefinitionKey,
      });
    }

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
              onChange={({key, versions, tenantIds, identifier}) =>
                this.props.onChange({
                  processDefinitionKey: key,
                  processDefinitionVersions: versions,
                  tenantIds,
                  identifier,
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
              definitions={definitions}
              filterLevel="instance"
              variables={this.state.variables}
            />
          </li>
        </ul>
      </div>
    );
  }
}

export default withErrorHandling(BranchControlPanel);
