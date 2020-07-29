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
import SpinnerSkeleton from 'modules/components/SpinnerSkeleton';
import Diagram from 'modules/components/Diagram';
import * as Styled from './styled.js';
import {instancesDiagram} from 'modules/stores/instancesDiagram';
import {observer} from 'mobx-react';

const DiagramPanel = observer(
  class DiagramPanel extends React.Component {
    static propTypes = {
      dataManager: PropTypes.object,
      expandState: PropTypes.oneOf(Object.values(EXPAND_STATE)).isRequired,
      noWorkflowSelected: PropTypes.bool.isRequired,
      noVersionSelected: PropTypes.bool.isRequired,
      flowNodesStatistics: PropTypes.array,
      onFlowNodeSelection: PropTypes.func,
      workflowName: PropTypes.string,
      activityId: PropTypes.string,
    };

    constructor(props) {
      super(props);

      this.state = {
        isLoading: '',
      };
      this.subscriptions = {
        LOAD_STATE_STATISTICS: (response) => {
          if (response.state === LOADING_STATE.LOADED) {
            this.setState({isLoading: LOADING_STATE.LOADED});
          }
        },
      };
    }

    componentDidMount() {
      this.props.dataManager.subscribe(this.subscriptions);
    }

    componentWillUnmount() {
      this.props.dataManager.unsubscribe(this.subscriptions);
    }

    renderMessage = (type) => {
      const message = {
        NoWorkflow: `There is no Workflow selected.\n To see a diagram, select a Workflow in the Filters panel.`,
        NoVersion: `There is more than one version selected for Workflow "${this.props.workflowName}".\n To see a diagram, select a single version.`,
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
        onFlowNodeSelection,
        flowNodesStatistics,
        workflowName,
        dataManager,
        activityId,
        ...paneProps
      } = this.props;

      const {
        isLoading: areStateDefinitionsLoading,
        diagramModel,
      } = instancesDiagram.state;

      const {selectableIds} = instancesDiagram;
      const selectedFlowNodeId = selectableIds.includes(activityId)
        ? activityId
        : undefined;
      return (
        <SplitPane.Pane {...paneProps}>
          <Styled.PaneHeader>
            <span data-test="instances-diagram-title">{workflowName}</span>
          </Styled.PaneHeader>
          <SplitPane.Pane.Body style={{position: 'relative'}}>
            {(this.state.isLoading === LOADING_STATE.LOADING ||
              areStateDefinitionsLoading) && (
              <SpinnerSkeleton data-test="spinner" />
            )}
            {noWorkflowSelected && this.renderMessage('NoWorkflow')}
            {noVersionSelected
              ? this.renderMessage('NoVersion')
              : diagramModel?.definitions && (
                  <Diagram
                    definitions={diagramModel.definitions}
                    onFlowNodeSelection={onFlowNodeSelection}
                    flowNodesStatistics={flowNodesStatistics}
                    selectedFlowNodeId={selectedFlowNodeId}
                    selectableFlowNodes={selectableIds}
                    expandState={paneProps.expandState}
                  />
                )}
          </SplitPane.Pane.Body>
        </SplitPane.Pane>
      );
    }
  }
);

const WrappedDiagramPanel = withData(DiagramPanel);
WrappedDiagramPanel.WrappedComponent = DiagramPanel;

export default WrappedDiagramPanel;
