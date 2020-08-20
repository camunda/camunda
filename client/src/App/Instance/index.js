/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component} from 'react';
import PropTypes from 'prop-types';

import SplitPane from 'modules/components/SplitPane';
import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';

import {
  SUBSCRIPTION_TOPIC,
  LOADING_STATE,
  POLL_TOPICS,
} from 'modules/constants';

import {withData} from 'modules/DataManager';

import {FlowNodeInstanceLog} from './FlowNodeInstanceLog';
import {TopPanel} from './TopPanel';
import BottomPanel from './BottomPanel';
import {VariablePanel} from './BottomPanel/VariablePanel';
import {isRunningInstance} from './service';
import {statistics} from 'modules/stores/statistics';
import {currentInstance} from 'modules/stores/currentInstance';
import {flowNodeInstance} from 'modules/stores/flowNodeInstance';
import {flowNodeTimeStamp} from 'modules/stores/flowNodeTimeStamp';
import {singleInstanceDiagram} from 'modules/stores/singleInstanceDiagram';
import {observer} from 'mobx-react';
import {autorun} from 'mobx';

import * as Styled from './styled';
const Instance = observer(
  class Instance extends Component {
    static propTypes = {
      dataManager: PropTypes.object,
      match: PropTypes.shape({
        params: PropTypes.shape({
          id: PropTypes.string.isRequired,
        }).isRequired,
      }).isRequired,
    };

    constructor(props) {
      super(props);

      this.disposer = null;
      this.state = {
        forceInstanceSpinner: false,
        isPollActive: false,
      };
      this.pollingTimer = null;
      this.subscriptions = {
        LOAD_INSTANCE: ({response, state}) => {
          if (state === LOADING_STATE.LOADED) {
            // kick off all follow-up requests.
            singleInstanceDiagram.fetchWorkflowXml(response.workflowId);
            flowNodeInstance.setCurrentSelection({
              flowNodeId: null,
              treeRowIds: [response.id],
            });
          }
        },

        CONSTANT_REFRESH: ({state}) => {
          if (state === LOADING_STATE.LOADED) {
            this.setState({
              isPollActive: false,
            });
          }
        },
      };
    }

    componentDidMount() {
      this.props.dataManager.subscribe(this.subscriptions);
      const id = this.props.match.params.id;
      // should later be removed and call currentInstance.fetchCurrentInstance(id); instead
      this.props.dataManager.getWorkflowInstance(id);
      flowNodeInstance.fetchInstanceExecutionHistory(id);
      flowNodeInstance.startPolling(id);

      this.disposer = autorun(() => {
        if (currentInstance.workflowTitle !== null)
          document.title = currentInstance.workflowTitle;
      });
    }

    componentDidUpdate() {
      if (
        this.isAllDataLoaded() &&
        !this.state.isPollActive &&
        isRunningInstance(currentInstance.state.instance)
      ) {
        this.setState({isPollActive: true}, () => {
          this.props.dataManager.poll.register(
            POLL_TOPICS.INSTANCE,
            this.handlePoll
          );
        });
      }
    }

    componentWillUnmount() {
      this.props.dataManager.unsubscribe(this.subscriptions);
      this.props.dataManager.poll.unregister(POLL_TOPICS.INSTANCE);
      flowNodeInstance.reset();
      currentInstance.reset();
      flowNodeTimeStamp.reset();
      singleInstanceDiagram.reset();
      if (this.disposer !== null) {
        this.disposer();
      }
    }

    handlePoll = () => {
      let updateParams = {
        topic: SUBSCRIPTION_TOPIC.CONSTANT_REFRESH,
        endpoints: [
          {name: SUBSCRIPTION_TOPIC.LOAD_INSTANCE}, // should later be removed and call currentInstance.fetchCurrentInstance(id); instead
        ],
      };
      statistics.fetchStatistics();
      this.props.dataManager.update(updateParams);
    };

    isAllDataLoaded = () => {
      const {
        forceInstanceSpinner,
        isPollActive,
        ...initallyLoadedStates
      } = this.state;

      const {flowNodeIdToFlowNodeInstanceMap} = flowNodeInstance;
      const {instance} = currentInstance.state;
      const states = {
        ...initallyLoadedStates,
        flowNodeIdToFlowNodeInstanceMap,
        instance,
      };
      return Object.values(states).reduce((acc, stateValue) => {
        return acc && Boolean(stateValue);
      }, true);
    };

    /**
     * Handles selecting a node row in the tree
     * @param {object} node: selected row node
     */
    handleTreeRowSelection = async (node) => {
      const {
        state: {selection},
        areMultipleNodesSelected,
      } = flowNodeInstance;

      const {instance} = currentInstance.state;

      const isRootNode = node.id === instance.id;
      // get the first flow node id (i.e. activity id) corresponding to the flowNodeId
      const flowNodeId = isRootNode ? null : node.activityId;
      const isRowAlreadySelected = selection.treeRowIds.includes(node.id);

      const newSelection =
        isRowAlreadySelected && !areMultipleNodesSelected
          ? {flowNodeId: null, treeRowIds: [instance.id]}
          : {flowNodeId, treeRowIds: [node.id]};

      flowNodeInstance.setCurrentSelection(newSelection);
    };

    render() {
      const {instance} = currentInstance.state;
      return (
        <Styled.Instance>
          <VisuallyHiddenH1>
            {instance && `Camunda Operate Instance ${instance.id}`}
          </VisuallyHiddenH1>
          <SplitPane
            titles={{top: 'Workflow', bottom: 'Instance Details'}}
            expandedPaneId="instanceExpandedPaneId"
          >
            <TopPanel
              onInstanceOperation={this.handleInstanceOperation}
              onTreeRowSelection={this.handleTreeRowSelection}
            />
            <BottomPanel>
              <FlowNodeInstanceLog
                onTreeRowSelection={this.handleTreeRowSelection}
              />
              <VariablePanel />
            </BottomPanel>
          </SplitPane>
        </Styled.Instance>
      );
    }
  }
);

const WrappedInstance = withData(Instance);
WrappedInstance.WrappedComponent = Instance;

export default WrappedInstance;
