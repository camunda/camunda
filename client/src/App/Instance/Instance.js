/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component, Fragment} from 'react';
import PropTypes from 'prop-types';

import SplitPane from 'modules/components/SplitPane';
import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';
import Diagram from 'modules/components/Diagram';
import IncidentsWrapper from './IncidentsWrapper';
import {PAGE_TITLE, UNNAMED_ACTIVITY, STATE} from 'modules/constants';
import {compactObject, immutableArraySet} from 'modules/utils';
import {getWorkflowName} from 'modules/utils/instance';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {formatDate} from 'modules/utils/date';
import * as api from 'modules/api/instances';
import {fetchWorkflowXML} from 'modules/api/diagram';
import {fetchEvents} from 'modules/api/events';

import FlowNodeInstancesTree from './FlowNodeInstancesTree';

import InstanceDetail from './InstanceDetail';
import Header from '../Header';
import DiagramPanel from './DiagramPanel';
import InstanceHistory from './InstanceHistory';
import Variables from './InstanceHistory/Variables';
import {
  getFlowNodeStateOverlays,
  getActivityIdToNameMap,
  isRunningInstance,
  fetchActivityInstancesTreeData,
  fetchIncidents,
  fetchVariables
} from './service';
import * as Styled from './styled';

const POLLING_WINDOW = 5000;

export default class Instance extends Component {
  static propTypes = {
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
      activityIdToNameMap: null,
      diagramDefinitions: null,
      loaded: false,
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
      editMode: ''
    };

    this.pollingTimer = null;
  }

  async componentDidMount() {
    const id = this.props.match.params.id;
    const instance = await api.fetchWorkflowInstance(id);

    document.title = PAGE_TITLE.INSTANCE(
      instance.id,
      getWorkflowName(instance)
    );

    const selection = {
      flowNodeId: null,
      treeRowIds: [instance.id]
    };

    const [
      {activityInstancesTree, activityIdToActivityInstanceMap},
      diagramXml,
      events,
      incidents,
      variables
    ] = await Promise.all([
      fetchActivityInstancesTreeData(instance),
      fetchWorkflowXML(instance.workflowId),
      fetchEvents(instance.id),
      fetchIncidents(instance),
      fetchVariables(instance, selection)
    ]);

    const {bpmnElements, definitions} = await parseDiagramXML(diagramXml);

    const activityIdToNameMap = getActivityIdToNameMap(bpmnElements);

    this.setState(
      {
        loaded: true,
        instance,
        ...(incidents && {incidents}),
        activityIdToNameMap,
        diagramDefinitions: definitions,
        events,
        activityInstancesTree,
        activityIdToActivityInstanceMap,
        selection,
        variables
      },
      () => {
        this.initializePolling();
      }
    );
  }

  componentWillUnmount() {
    this.clearPolling();
  }

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
      const variables = await api.fetchVariables(instance.id, scopeId);
      this.setState({variables, editMode: ''});
    }
  };

  /**
   * Handles selecting a flow node from the diagram
   * @param {string} flowNodeId: id of the selected flow node
   */
  handleFlowNodeSelection = async flowNodeId => {
    const {instance, activityIdToActivityInstanceMap} = this.state;
    // get the first activity instance corresponding to the flowNodeId
    const treeRowIds = !flowNodeId
      ? [instance.id]
      : [...activityIdToActivityInstanceMap.get(flowNodeId).keys()];

    this.setState({
      selection: {
        treeRowIds,
        flowNodeId
      },
      // clear variables object if we don't have exactly 1 selected row
      ...(treeRowIds.length !== 1 && {variables: null, editMode: ''})
    });

    if (treeRowIds.length === 1) {
      const scopeId = treeRowIds[0];
      const variables = await api.fetchVariables(instance.id, scopeId);
      this.setState({variables, editMode: ''});
    }
  };

  initializePolling = () => {
    const {instance} = this.state;

    if (isRunningInstance(instance.state)) {
      this.pollingTimer = setTimeout(this.detectChangesPoll, POLLING_WINDOW);
    }
  };

  resetPolling = () => {
    this.clearPolling();
    this.initializePolling();
  };

  clearPolling = () => {
    this.pollingTimer && clearTimeout(this.pollingTimer);
    this.pollingTimer = null;
  };

  detectChangesPoll = async () => {
    let requestsPromises = [
      api.fetchWorkflowInstance(this.state.instance.id),
      fetchIncidents(this.state.instance),
      fetchActivityInstancesTreeData(this.state.instance),
      fetchEvents(this.state.instance.id),
      fetchVariables(this.state.instance, this.state.selection)
    ];

    const [
      instance,
      incidents,
      {activityInstancesTree, activityIdToActivityInstanceMap},
      events,
      variables
    ] = await Promise.all(requestsPromises);

    this.setState(
      {
        instance,
        ...(incidents && {incidents}),
        events,
        activityInstancesTree,
        activityIdToActivityInstanceMap,
        ...(variables && {variables}),
        forceInstanceSpinner: false,
        forceIncidentsSpinner: false
      },
      () => {
        this.initializePolling();
      }
    );
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
        instancesCount: activityInstancesMap.size
      };
    }

    // get the last event corresponding to the given flowNodeId (= activityId)
    const {activityInstanceId, metadata} = events.reduce((acc, event) => {
      return event.activityInstanceId === treeRowIds[0] ? event : acc;
    }, null);

    // get corresponding start and end dates
    const activityInstance = activityIdToActivityInstanceMap
      .get(flowNodeId)
      .get(activityInstanceId);

    const startDate = formatDate(activityInstance.startDate);
    const endDate = formatDate(activityInstance.endDate);

    // return a cleaned-up  metadata object
    return {
      ...compactObject({
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

  getNodeWithName = node => {
    const {instance} = this.state;

    const name =
      node.id === instance.id
        ? getWorkflowName(instance)
        : this.state.activityIdToNameMap.get(node.activityId) ||
          UNNAMED_ACTIVITY;

    return {
      ...node,
      name
    };
  };

  addFLowNodeNames = incidents =>
    incidents.map(incident => this.addFlowNodeName(incident));

  mapify = (arrayOfObjects, uniqueKey, modifier) =>
    arrayOfObjects.reduce((acc, object) => {
      const modifiedObj = modifier ? modifier(object) : object;
      return acc.set(modifiedObj[uniqueKey], modifiedObj);
    }, new Map());

  addFlowNodeName = object => {
    const modifiedObject = {...object};
    modifiedObject.flowNodeName =
      this.state.activityIdToNameMap.get(modifiedObject.flowNodeId) ||
      UNNAMED_ACTIVITY;
    return modifiedObject;
  };

  handleIncidentOperation = () => {
    this.setState({forceInstanceSpinner: true});
  };

  handleInstanceOperation = () => {
    this.setState({forceIncidentsSpinner: true});
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

    return await api.applyOperation(id, {
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
      loaded,
      diagramDefinitions,
      instance,
      incidents,
      selection,
      activityIdToActivityInstanceMap,
      activityIdToNameMap,
      variables,
      editMode
    } = this.state;

    if (!loaded) {
      return 'Loading';
    }

    // Get extra information for the diagram
    const selectableFlowNodes = [...activityIdToActivityInstanceMap.keys()];

    const flowNodeStateOverlays = getFlowNodeStateOverlays(
      activityIdToActivityInstanceMap
    );

    const metadata = !selection.flowNodeId ? null : this.getCurrentMetadata();

    const selectedFlowNodeId = selection.flowNodeId;

    const selectedFlowNodeName = !selectedFlowNodeId
      ? null
      : activityIdToNameMap.get(selectedFlowNodeId) || selectedFlowNodeId;

    return (
      <Fragment>
        <Header detail={<InstanceDetail instance={instance} />} />
        <Styled.Instance>
          <VisuallyHiddenH1>
            {`Camunda Operate Instance ${this.state.instance.id}`}
          </VisuallyHiddenH1>
          <SplitPane titles={{top: 'Workflow', bottom: 'Instance Details'}}>
            <DiagramPanel
              instance={instance}
              forceInstanceSpinner={this.state.forceInstanceSpinner}
              onInstanceOperation={this.handleInstanceOperation}
            >
              {this.state.instance.state === 'INCIDENT' && (
                <IncidentsWrapper
                  incidents={this.addFLowNodeNames(incidents.incidents)}
                  incidentsCount={this.state.incidents.count}
                  instance={this.state.instance}
                  forceSpinner={this.state.forceIncidentsSpinner}
                  selectedFlowNodeInstanceIds={this.state.selection.treeRowIds}
                  onIncidentOperation={this.handleIncidentOperation}
                  onIncidentSelection={this.handleTreeRowSelection}
                  flowNodes={this.mapify(
                    incidents.flowNodes,
                    'flowNodeId',
                    this.addFlowNodeName
                  )}
                  errorTypes={this.mapify(incidents.errorTypes, 'errorType')}
                />
              )}
              {diagramDefinitions && (
                <Diagram
                  onFlowNodeSelection={this.handleFlowNodeSelection}
                  selectableFlowNodes={selectableFlowNodes}
                  selectedFlowNodeId={selectedFlowNodeId}
                  selectedFlowNodeName={selectedFlowNodeName}
                  flowNodeStateOverlays={flowNodeStateOverlays}
                  definitions={diagramDefinitions}
                  metadata={metadata}
                />
              )}
            </DiagramPanel>
            <InstanceHistory>
              <Styled.Panel>
                <Styled.FlowNodeInstanceLog>
                  <Styled.NodeContainer>
                    <FlowNodeInstancesTree
                      node={this.state.activityInstancesTree}
                      getNodeWithName={this.getNodeWithName}
                      treeDepth={1}
                      selectedTreeRowIds={this.state.selection.treeRowIds}
                      onTreeRowSelection={this.handleTreeRowSelection}
                    />
                  </Styled.NodeContainer>
                </Styled.FlowNodeInstanceLog>
              </Styled.Panel>
              <Variables
                instanceState={this.state.instance.state}
                variables={variables}
                editMode={editMode}
                isEditable={this.areVariablesEditable()}
                onVariableUpdate={this.handleVariableUpdate}
                setEditMode={this.setEditMode}
              />
            </InstanceHistory>
          </SplitPane>
        </Styled.Instance>
      </Fragment>
    );
  }
}
