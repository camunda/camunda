/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {LOADING_STATE, EXPAND_STATE} from 'modules/constants';
import SplitPane from 'modules/components/SplitPane';
import {withData} from 'modules/DataManager';
import SpinnerSkeleton from 'modules/components/Skeletons';
import Diagram from 'modules/components/Diagram';
import * as Styled from './styled.js';

class DiagramPanel extends React.Component {
  static propTypes = {
    dataManager: PropTypes.object,
    expandState: PropTypes.oneOf(Object.values(EXPAND_STATE)),
    noWorkflowSelected: PropTypes.bool.isRequired,
    noVersionSelected: PropTypes.bool.isRequired,
    selectableFlowNodes: PropTypes.array,
    flowNodesStatistics: PropTypes.array,
    definitions: PropTypes.object,
    selectedFlowNodeId: PropTypes.string,
    onFlowNodeSelection: PropTypes.func,
    workflowName: PropTypes.string
  };

  constructor(props) {
    super(props);

    this.state = {
      isLoading: ''
    };
    this.subscriptions = {
      LOAD_STATE_DEFINITIONS: response => {
        if (response.state === LOADING_STATE.LOADING) {
          this.setState({isLoading: LOADING_STATE.LOADING});
        }
      },
      LOAD_STATE_STATISTICS: response => {
        if (response.state === LOADING_STATE.LOADED) {
          this.setState({isLoading: LOADING_STATE.LOADED});
        }
      }
    };
  }

  componentDidMount() {
    this.props.dataManager.subscribe(this.subscriptions);
  }

  componentWillUnmount() {
    this.props.dataManager.unsubscribe(this.subscriptions);
  }

  renderMessage = type => {
    const message = {
      NoWorkflow: `There is no Workflow selected.\n To see a diagram, select a Workflow in the Filters panel.`,
      NoVersion: `There is more than one version selected for Workflow "${this.props.workflowName}".\n To see a diagram, select a single version.`
    };
    return (
      <Styled.EmptyMessageWrapper>
        <Styled.DiagramEmptyMessage message={message[type]} />
      </Styled.EmptyMessageWrapper>
    );
  };

  render() {
    const {
      noWorkflowSelected,
      noVersionSelected,
      selectableFlowNodes,
      selectedFlowNodeId,
      definitions,
      onFlowNodeSelection,
      flowNodesStatistics,
      workflowName,
      dataManager,
      ...paneProps
    } = this.props;
    return (
      <SplitPane.Pane {...paneProps}>
        <Styled.PaneHeader>
          <span data-test="instances-diagram-title">{workflowName}</span>
        </Styled.PaneHeader>
        <SplitPane.Pane.Body style={{position: 'relative'}}>
          {this.state.isLoading === LOADING_STATE.LOADING && (
            <SpinnerSkeleton data-test="spinner" />
          )}
          {noWorkflowSelected && this.renderMessage('NoWorkflow')}
          {noVersionSelected
            ? this.renderMessage('NoVersion')
            : !!definitions && (
                <Diagram
                  definitions={definitions}
                  onFlowNodeSelection={onFlowNodeSelection}
                  flowNodesStatistics={flowNodesStatistics}
                  selectedFlowNodeId={selectedFlowNodeId}
                  selectableFlowNodes={selectableFlowNodes}
                />
              )}
        </SplitPane.Pane.Body>
      </SplitPane.Pane>
    );
  }
}

const WrappedDiagramPanel = withData(DiagramPanel);
WrappedDiagramPanel.WrappedComponent = DiagramPanel;

export default WrappedDiagramPanel;
