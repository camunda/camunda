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
  PAGE_TITLE,
  SUBSCRIPTION_TOPIC,
  TYPE,
  LOADING_STATE,
  POLL_TOPICS,
} from 'modules/constants';

import {compactObject} from 'modules/utils';
import {getWorkflowName} from 'modules/utils/instance';
import {formatDate} from 'modules/utils/date';
import {withData} from 'modules/DataManager';

import FlowNodeInstanceLog from './FlowNodeInstanceLog';
import TopPanel from './TopPanel';
import BottomPanel from './BottomPanel';
import {VariablePanel} from './BottomPanel/VariablePanel';
import {
  isRunningInstance,
  createNodeMetaDataMap,
  getSelectableFlowNodes,
  storeResponse,
  getProcessedSequenceFlows,
} from './service';
import {statistics} from 'modules/stores/statistics';
import {currentInstance} from 'modules/stores/currentInstance';
import {flowNodeInstance} from 'modules/stores/flowNodeInstance';
import {observer} from 'mobx-react';

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

      this.state = {
        nodeMetaDataMap: null,
        diagramDefinitions: null,
        processedSequenceFlows: [],
        events: [],
        incidents: {
          count: 0,
          incidents: [],
          flowNodes: [],
          errorTypes: [],
        },
        forceInstanceSpinner: false,
        forceIncidentsSpinner: false,
        isPollActive: false,
      };
      this.pollingTimer = null;
      this.subscriptions = {
        LOAD_INSTANCE: ({response, state}) => {
          if (state === LOADING_STATE.LOADED) {
            document.title = PAGE_TITLE.INSTANCE(
              response.id,
              getWorkflowName(response)
            );

            const {dataManager} = this.props;
            // kick off all follow-up requests.
            dataManager.getWorkflowXML(response.workflowId, response);
            dataManager.getEvents(response.id);
            dataManager.getSequenceFlows(response.id);

            if (response.state === 'INCIDENT') {
              dataManager.getIncidents(response);
            }
          }
        },
        LOAD_INCIDENTS: (responseData) =>
          storeResponse(responseData, (response) => {
            this.setState({incidents: response});
          }),
        LOAD_EVENTS: (responseData) =>
          storeResponse(responseData, (response) => {
            this.setState({events: response});
          }),

        LOAD_STATE_DEFINITIONS: ({response, state, staticContent}) => {
          if (state === LOADING_STATE.LOADED) {
            // Get all selectable BPMN elements
            const nodeMetaDataMap = createNodeMetaDataMap(
              getSelectableFlowNodes(response.bpmnElements)
            );
            const selection = {
              flowNodeId: null,
              treeRowIds: [staticContent.id],
            };

            this.setState({
              nodeMetaDataMap: nodeMetaDataMap,
              diagramDefinitions: response.definitions,
            });

            flowNodeInstance.setCurrentSelection(selection);
          }
        },
        LOAD_SEQUENCE_FLOWS: ({response, state}) => {
          if (state === LOADING_STATE.LOADED) {
            this.setState({
              processedSequenceFlows: getProcessedSequenceFlows(response),
            });
          }
        },
        CONSTANT_REFRESH: ({response, state}) => {
          if (state === LOADING_STATE.LOADED) {
            const {LOAD_INCIDENTS, LOAD_EVENTS, LOAD_SEQUENCE_FLOWS} = response;

            this.setState({
              isPollActive: false,
              events: LOAD_EVENTS,
              processedSequenceFlows: getProcessedSequenceFlows(
                LOAD_SEQUENCE_FLOWS
              ),
              // conditional updates
              ...(LOAD_INCIDENTS && {incidents: LOAD_INCIDENTS}),
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
    }

    handlePoll = () => {
      let updateParams = {
        topic: SUBSCRIPTION_TOPIC.CONSTANT_REFRESH,
        endpoints: [
          {name: SUBSCRIPTION_TOPIC.LOAD_INSTANCE}, // should later be removed and call currentInstance.fetchCurrentInstance(id); instead
          {name: SUBSCRIPTION_TOPIC.LOAD_INCIDENTS},
          {name: SUBSCRIPTION_TOPIC.LOAD_EVENTS},
          {name: SUBSCRIPTION_TOPIC.LOAD_SEQUENCE_FLOWS},
        ],
      };
      statistics.fetchStatistics();

      this.props.dataManager.update(updateParams);
    };

    isAllDataLoaded = () => {
      const {
        forceInstanceSpinner,
        forceIncidentsSpinner,
        isPollActive,
        ...initallyLoadedStates
      } = this.state;

      const {flowNodeIdToFlowNodeInstanceMap} = flowNodeInstance;
      const states = {
        ...initallyLoadedStates,
        flowNodeIdToFlowNodeInstanceMap,
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

    getCurrentMetadata = () => {
      const {events} = this.state;
      const {
        state: {
          selection: {flowNodeId, treeRowIds},
        },
        flowNodeIdToFlowNodeInstanceMap,
        areMultipleNodesSelected,
      } = flowNodeInstance;

      const flowNodeInstancesMap = flowNodeIdToFlowNodeInstanceMap.get(
        flowNodeId
      );

      // Peter case with more than 1 tree row selected
      if (areMultipleNodesSelected) {
        return {
          isMultiRowPeterCase: true,
          instancesCount: treeRowIds.length,
        };
      }

      // get the last event corresponding to the given flowNodeId (= activityId)
      const {activityInstanceId, metadata} = events.reduce((acc, event) => {
        return event.activityInstanceId === treeRowIds[0] ? event : acc;
      }, null);

      // get corresponding start and end dates
      const activityInstance = flowNodeInstancesMap.get(activityInstanceId);

      const startDate = formatDate(activityInstance.startDate);
      const endDate = formatDate(activityInstance.endDate);

      const isMultiInstanceBody =
        activityInstance.type === TYPE.MULTI_INSTANCE_BODY;

      const parentActivity = flowNodeInstancesMap.get(
        activityInstance.parentId
      );
      const isMultiInstanceChild =
        parentActivity && parentActivity.type === TYPE.MULTI_INSTANCE_BODY;

      // return a cleaned-up  metadata object
      return {
        ...compactObject({
          isMultiInstanceBody,
          isMultiInstanceChild,
          parentId: activityInstance.parentId,
          isSingleRowPeterCase: flowNodeInstancesMap.size > 1 ? true : null,
        }),
        data: Object.entries({
          activityInstanceId,
          ...metadata,
          startDate,
          endDate,
        }).reduce((cleanMetadata, [key, value]) => {
          // ignore other empty values
          if (!value) {
            return cleanMetadata;
          }

          return {...cleanMetadata, [key]: value};
        }, {}),
      };
    };

    getNodeWithMetaData = (node) => {
      const metaData = this.state.nodeMetaDataMap.get(node.activityId) || {
        name: undefined,
        type: {
          elementType: undefined,
          eventType: undefined,
          multiInstanceType: undefined,
        },
      };

      const typeDetails = {
        ...metaData.type,
      };

      if (node.type === TYPE.WORKFLOW) {
        typeDetails.elementType = TYPE.WORKFLOW;
      }

      if (node.type === TYPE.MULTI_INSTANCE_BODY) {
        typeDetails.elementType = TYPE.MULTI_INSTANCE_BODY;
      }

      // Add Node Name
      const nodeName =
        node.id === currentInstance.state.instance.id
          ? getWorkflowName(currentInstance.state.instance)
          : (metaData && metaData.name) || node.activityId;

      return {
        ...node,
        typeDetails,
        name: nodeName,
      };
    };

    render() {
      const {
        diagramDefinitions,
        incidents,
        nodeMetaDataMap,
        processedSequenceFlows,
      } = this.state;
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
              incidents={incidents}
              nodeMetaDataMap={nodeMetaDataMap}
              diagramDefinitions={diagramDefinitions}
              processedSequenceFlows={processedSequenceFlows}
              getCurrentMetadata={this.getCurrentMetadata}
              onInstanceOperation={this.handleInstanceOperation}
              onTreeRowSelection={this.handleTreeRowSelection}
            />
            <BottomPanel>
              <FlowNodeInstanceLog
                diagramDefinitions={diagramDefinitions}
                getNodeWithMetaData={this.getNodeWithMetaData}
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
