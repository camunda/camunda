/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component, Fragment} from 'react';
import PropTypes from 'prop-types';

import SplitPane from 'modules/components/SplitPane';
import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';

import {
  PAGE_TITLE,
  UNNAMED_ACTIVITY,
  SUBSCRIPTION_TOPIC,
  STATE,
  TYPE,
  LOADING_STATE
} from 'modules/constants';

import {compactObject, immutableArraySet} from 'modules/utils';
import {getWorkflowName} from 'modules/utils/instance';
import {formatDate} from 'modules/utils/date';
import {withData} from 'modules/DataManager';

import FlowNodeInstanceLog from './FlowNodeInstanceLog';
import InstanceDetail from './InstanceDetail';
import Header from '../Header';
import TopPanel from './TopPanel';
import BottomPanel from './BottomPanel';
import VariablePanel from './BottomPanel/VariablePanel';
import {
  isRunningInstance,
  createNodeMetaDataMap,
  getSelectableFlowNodes,
  getActivityIdToActivityInstancesMap,
  getMultiInstanceBodies,
  getMultiInstanceChildren,
  storeResponse
} from './service';
import * as Styled from './styled';

class Instance extends Component {
  static propTypes = {
    dataManager: PropTypes.object,
    match: PropTypes.shape({
      params: PropTypes.shape({
        id: PropTypes.string.isRequired
      }).isRequired
    }).isRequired
  };

  constructor(props) {
    super(props);

    this.state = {
      instance: null,
      selection: {
        treeRowIds: [],
        flowNodeId: null
      },
      nodeMetaDataMap: null,
      diagramDefinitions: null,
      activityInstancesTree: {},
      activityIdToActivityInstanceMap: null,
      events: [],
      incidents: {
        count: 0,
        incidents: [],
        flowNodes: [],
        errorTypes: []
      },
      forceInstanceSpinner: false,
      forceIncidentsSpinner: false,
      variables: null,
      editMode: '',
      isPollActive: false
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
          dataManager.getActivityInstancesTreeData(response);
          dataManager.getWorkflowXML(response.workflowId, response);
          dataManager.getEvents(response.id);
          dataManager.getVariables(response.id, response.id);

          if (response.state === 'INCIDENT') {
            dataManager.getIncidents(response);
          }

          this.setState({
            instance: response
          });
        }
      },
      LOAD_VARIABLES: responseData =>
        storeResponse(responseData, response => {
          this.setState({variables: response});
        }),
      LOAD_INCIDENTS: responseData =>
        storeResponse(responseData, response => {
          this.setState({incidents: response});
        }),
      LOAD_EVENTS: responseData =>
        storeResponse(responseData, response => {
          this.setState({events: response});
        }),

      LOAD_INSTANCE_TREE: ({response, state, staticContent}) => {
        if (state === LOADING_STATE.LOADED) {
          this.setState({
            activityIdToActivityInstanceMap: getActivityIdToActivityInstancesMap(
              response
            ),
            activityInstancesTree: {
              ...response,
              id: staticContent.id,
              type: 'WORKFLOW',
              state: staticContent.state,
              endDate: staticContent.endDate
            }
          });
        }
      },
      LOAD_STATE_DEFINITIONS: ({response, state, staticContent}) => {
        if (state === LOADING_STATE.LOADED) {
          // Get all selectable BPMN elements
          const nodeMetaDataMap = createNodeMetaDataMap(
            getSelectableFlowNodes(response.bpmnElements)
          );
          const selection = {
            flowNodeId: null,
            treeRowIds: [staticContent.id]
          };

          this.setState({
            nodeMetaDataMap: nodeMetaDataMap,
            diagramDefinitions: response.definitions,
            selection
          });
        }
      },
      CONSTANT_REFRESH: ({response, state}) => {
        if (state === LOADING_STATE.LOADED) {
          const {
            LOAD_INSTANCE,
            LOAD_VARIABLES,
            LOAD_INCIDENTS,
            LOAD_EVENTS,
            LOAD_INSTANCE_TREE
          } = response;

          this.setState({
            isPollActive: false,
            instance: LOAD_INSTANCE,
            events: LOAD_EVENTS,
            activityInstancesTree: {
              ...LOAD_INSTANCE_TREE,
              id: LOAD_INSTANCE.id,
              type: 'WORKFLOW',
              state: LOAD_INSTANCE.state,
              endDate: LOAD_INSTANCE.endDate
            },
            activityIdToActivityInstanceMap: getActivityIdToActivityInstancesMap(
              LOAD_INSTANCE_TREE
            ),
            // conditional updates
            ...(LOAD_INCIDENTS && {incidents: LOAD_INCIDENTS}),
            ...(LOAD_VARIABLES && {variables: LOAD_VARIABLES})
          });
        }
      }
    };
  }

  componentDidMount() {
    this.props.dataManager.subscribe(this.subscriptions);
    const id = this.props.match.params.id;
    this.props.dataManager.getWorkflowInstance(id);
  }

  componentDidUpdate() {
    if (
      this.isAllDataLoaded() &&
      !this.state.isPollActive &&
      isRunningInstance(this.state.instance)
    ) {
      this.setState({isPollActive: true}, () => {
        this.props.dataManager.poll.start(() => this.handlePoll());
      });
    }
  }

  componentWillUnmount() {
    this.props.dataManager.unsubscribe(this.subscriptions);
    this.props.dataManager.poll.clear();
  }

  handlePoll() {
    let updateParams = {
      topic: SUBSCRIPTION_TOPIC.CONSTANT_REFRESH,
      endpoints: [
        {name: SUBSCRIPTION_TOPIC.LOAD_INSTANCE},
        {name: SUBSCRIPTION_TOPIC.LOAD_INCIDENTS},
        {name: SUBSCRIPTION_TOPIC.LOAD_EVENTS},
        {name: SUBSCRIPTION_TOPIC.LOAD_INSTANCE_TREE}
      ]
    };
    const {selection, instance} = this.state;
    if (selection.treeRowIds.length === 1) {
      updateParams.endpoints = [
        ...updateParams.endpoints,
        {
          name: SUBSCRIPTION_TOPIC.LOAD_VARIABLES,
          payload: {instanceId: instance.id, scopeId: selection.treeRowIds[0]}
        }
      ];
    }
    this.props.dataManager.update(updateParams);
  }

  isAllDataLoaded = () => {
    const {
      forceInstanceSpinner,
      forceIncidentsSpinner,
      editMode,
      isPollActive,
      ...initallyLoadedStates
    } = this.state;

    return Object.values(initallyLoadedStates).reduce((acc, stateValue) => {
      return acc && Boolean(stateValue);
    }, true);
  };

  /**
   * Handles selecting a node row in the tree
   * @param {object} node: selected row node
   */
  handleTreeRowSelection = async node => {
    const {selection, instance} = this.state;
    const isRootNode = node.id === instance.id;

    // get the first flow node id (i.e. activity id) corresponding to the flowNodeId
    const flowNodeId = isRootNode ? null : node.activityId;
    const hasSelectedSiblings = selection.treeRowIds.length > 1;
    const rowIsSelected = !!selection.treeRowIds.find(
      selectedId => selectedId === node.id
    );
    const newSelection =
      rowIsSelected && !hasSelectedSiblings
        ? {flowNodeId: null, treeRowIds: [instance.id]}
        : {flowNodeId, treeRowIds: [node.id]};

    this.setState({
      selection: newSelection,
      // clear variables object if we don't have exactly 1 selected row
      ...(newSelection.treeRowIds.length !== 1 && {
        variables: null,
        editMode: ''
      })
    });

    if (newSelection.treeRowIds.length === 1) {
      const scopeId = newSelection.treeRowIds[0];
      this.props.dataManager.getVariables(instance.id, scopeId);
      this.setState({editMode: ''});
    }
  };

  /**
   * Handles selecting a flow node from the diagram
   * @param {string} flowNodeId: id of the selected flow node
   * @param {Object} options: refine, which instances to select
   */
  handleFlowNodeSelection = async (
    flowNodeId,
    options = {
      selectMultiInstanceChildrenOnly: false
    }
  ) => {
    const {instance, activityIdToActivityInstanceMap} = this.state;

    let treeRowIds = [instance.id];

    if (flowNodeId) {
      const activityInstancesMap = activityIdToActivityInstanceMap.get(
        flowNodeId
      );

      treeRowIds = options.selectMultiInstanceChildrenOnly
        ? getMultiInstanceChildren(activityInstancesMap)
        : getMultiInstanceBodies(activityInstancesMap);
    }

    const selection = {...this.state.selection, treeRowIds, flowNodeId};

    // get the first activity instance corresponding to the flowNodeId
    this.setState({
      selection,
      // clear variables object if we don't have exactly 1 selected row
      ...(treeRowIds.length !== 1 && {variables: null, editMode: ''})
    });

    if (treeRowIds.length === 1) {
      const scopeId = treeRowIds[0];
      this.props.dataManager.getVariables(instance.id, scopeId);
      this.setState({editMode: ''});
    }
  };

  getCurrentMetadata = () => {
    const {
      selection: {flowNodeId, treeRowIds},
      events,
      activityIdToActivityInstanceMap
    } = this.state;

    const activityInstancesMap = activityIdToActivityInstanceMap.get(
      flowNodeId
    );

    // Peter case with more than 1 tree row selected
    if (treeRowIds.length > 1) {
      return {
        isMultiRowPeterCase: true,
        instancesCount: treeRowIds.length
      };
    }

    // get the last event corresponding to the given flowNodeId (= activityId)
    const {activityInstanceId, metadata} = events.reduce((acc, event) => {
      return event.activityInstanceId === treeRowIds[0] ? event : acc;
    }, null);

    // get corresponding start and end dates
    const activityInstance = activityInstancesMap.get(activityInstanceId);

    const startDate = formatDate(activityInstance.startDate);
    const endDate = formatDate(activityInstance.endDate);

    const isMultiInstanceBody =
      activityInstance.type === TYPE.MULTI_INSTANCE_BODY;

    const parentActivity = activityInstancesMap.get(activityInstance.parentId);
    const isMultiInstanceChild =
      parentActivity && parentActivity.type === TYPE.MULTI_INSTANCE_BODY;

    // return a cleaned-up  metadata object
    return {
      ...compactObject({
        isMultiInstanceBody,
        isMultiInstanceChild,
        parentId: activityInstance.parentId,
        isSingleRowPeterCase: activityInstancesMap.size > 1 ? true : null
      }),
      data: Object.entries({
        activityInstanceId,
        ...metadata,
        startDate,
        endDate
      }).reduce((cleanMetadata, [key, value]) => {
        // ignore other empty values
        if (!value) {
          return cleanMetadata;
        }

        return {...cleanMetadata, [key]: value};
      }, {})
    };
  };

  getNodeWithMetaData = node => {
    const metaData = this.state.nodeMetaDataMap.get(node.activityId) || {
      name: undefined,
      type: {
        elementType: undefined,
        eventType: undefined,
        multiInstanceType: undefined
      }
    };

    const typeDetails = {
      ...metaData.type
    };

    if (node.type === TYPE.WORKFLOW) {
      typeDetails.elementType = TYPE.WORKFLOW;
    }

    if (node.type === TYPE.MULTI_INSTANCE_BODY) {
      typeDetails.elementType = TYPE.MULTI_INSTANCE_BODY;
    }

    // Add Node Name
    const nodeName =
      node.id === this.state.instance.id
        ? getWorkflowName(this.state.instance)
        : (metaData && metaData.name) || UNNAMED_ACTIVITY;

    return {
      ...node,
      typeDetails,
      name: nodeName
    };
  };

  addFlowNodeName = object => {
    const modifiedObject = {...object};

    const nodeMetaData = this.state.nodeMetaDataMap.get(
      modifiedObject.flowNodeId
    );

    modifiedObject.flowNodeName =
      (nodeMetaData && nodeMetaData.name) || UNNAMED_ACTIVITY;
    return modifiedObject;
  };

  handleVariableUpdate = async (key, value) => {
    const {
      selection: {treeRowIds},
      instance: {id},
      variables
    } = this.state;

    const keyIdx = variables.findIndex(variable => variable.name === key);

    this.setState({
      variables: immutableArraySet(
        variables,
        keyIdx > -1 ? keyIdx : variables.length,
        {
          name: key,
          value,
          hasActiveOperation: true
        }
      )
    });

    return this.props.dataManager.applyOperation(id, {
      operationType: 'UPDATE_VARIABLE',
      scopeId: treeRowIds[0],
      name: key,
      value
    });
  };

  areVariablesEditable = () => {
    const {
      instance,
      activityIdToActivityInstanceMap,
      variables,
      selection: {flowNodeId, treeRowIds}
    } = this.state;

    if (!variables) {
      return false;
    }

    const selectedRowState = !flowNodeId
      ? instance.state
      : activityIdToActivityInstanceMap.get(flowNodeId).get(treeRowIds[0])
          .state;

    return [STATE.ACTIVE, STATE.INCIDENT].includes(selectedRowState);
  };

  setVariables = variables => {
    this.resetPolling();
    this.setState({variables, editMode: ''});
  };

  setEditMode = editMode => {
    this.setState({editMode});
  };

  render() {
    const {
      diagramDefinitions,
      instance,
      incidents,
      selection,
      activityIdToActivityInstanceMap,
      nodeMetaDataMap,
      activityInstancesTree,
      variables,
      editMode
    } = this.state;

    return (
      <Fragment>
        <Header detail={instance && <InstanceDetail instance={instance} />} />
        <Styled.Instance>
          <VisuallyHiddenH1>
            {instance && `Camunda Operate Instance ${instance.id}`}
          </VisuallyHiddenH1>
          <SplitPane titles={{top: 'Workflow', bottom: 'Instance Details'}}>
            <TopPanel
              instance={instance}
              selection={selection}
              incidents={incidents}
              nodeMetaDataMap={nodeMetaDataMap}
              diagramDefinitions={diagramDefinitions}
              activityIdToActivityInstanceMap={activityIdToActivityInstanceMap}
              getCurrentMetadata={this.getCurrentMetadata}
              onFlowNodeSelection={this.handleFlowNodeSelection}
              onInstanceOperation={this.handleInstanceOperation}
              onTreeRowSelection={this.handleTreeRowSelection}
            />
            <BottomPanel>
              <FlowNodeInstanceLog
                diagramDefinitions={diagramDefinitions}
                activityInstancesTree={activityInstancesTree}
                getNodeWithMetaData={this.getNodeWithMetaData}
                selectedTreeRowIds={this.state.selection.treeRowIds}
                onTreeRowSelection={this.handleTreeRowSelection}
              />
              <VariablePanel
                instance={instance}
                variables={variables}
                editMode={editMode}
                isEditable={this.areVariablesEditable()}
                onVariableUpdate={this.handleVariableUpdate}
                setEditMode={this.setEditMode}
              />
            </BottomPanel>
          </SplitPane>
        </Styled.Instance>
      </Fragment>
    );
  }
}

const WrappedInstance = withData(Instance);
WrappedInstance.WrappedComponent = Instance;

export default WrappedInstance;
