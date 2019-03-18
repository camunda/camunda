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
import {PAGE_TITLE, UNNAMED_ACTIVITY} from 'modules/constants';
import {compactObject} from 'modules/utils';
import {getWorkflowName} from 'modules/utils/instance';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {formatDate} from 'modules/utils/date';
import * as api from 'modules/api/instances';
import {fetchActivityInstancesTree} from 'modules/api/activityInstances';
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
  getActivityIdToActivityInstancesMap
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
        incidents: []
      },
      forceInstanceSpinner: false,
      forceIncidentsSpinner: false,
      variables: null
    };

    this.pollingTimer = null;
  }

  async componentDidMount() {
    const id = this.props.match.params.id;
    const instance = await api.fetchWorkflowInstance(id);
    let incidents = [];

    document.title = PAGE_TITLE.INSTANCE(
      instance.id,
      getWorkflowName(instance)
    );

    if (instance.state === 'INCIDENT') {
      incidents = await api.fetchWorkflowInstanceIncidents(id);
    }

    const [
      activitiesInstancesTree,
      diagramXml,
      events,
      variables
    ] = await Promise.all([
      fetchActivityInstancesTree(id),
      fetchWorkflowXML(instance.workflowId),
      fetchEvents(instance.id),
      api.fetchVariables(instance.id, instance.id)
    ]);

    const {bpmnElements, definitions} = await parseDiagramXML(diagramXml);

    const activityIdToNameMap = getActivityIdToNameMap(bpmnElements);

    const activityIdToActivityInstanceMap = getActivityIdToActivityInstancesMap(
      activitiesInstancesTree
    );

    this.setState(
      {
        loaded: true,
        instance,
        incidents,
        activityIdToNameMap,
        diagramDefinitions: definitions,
        events,
        activityInstancesTree: {
          ...activitiesInstancesTree,
          id: instance.id,
          type: 'WORKFLOW',
          state: instance.state,
          endDate: instance.endDate
        },
        activityIdToActivityInstanceMap,
        selection: {
          flowNodeId: null,
          treeRowIds: [instance.id]
        },
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
      ...(newSelection.treeRowIds.length !== 1 && {variables: null})
    });

    if (newSelection.treeRowIds.length === 1) {
      const scopeId = newSelection.treeRowIds[0];
      const variables = await api.fetchVariables(instance.id, scopeId);
      this.setState({variables});
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
      ...(treeRowIds.length !== 1 && {variables: null})
    });

    if (treeRowIds.length === 1) {
      const scopeId = treeRowIds[0];
      const variables = await api.fetchVariables(instance.id, scopeId);
      this.setState({variables});
    }
  };

  initializePolling = () => {
    const {instance} = this.state;

    if (isRunningInstance(instance.state)) {
      this.pollingTimer = setTimeout(this.detectChangesPoll, POLLING_WINDOW);
    }
  };

  clearPolling = () => {
    this.pollingTimer && clearTimeout(this.pollingTimer);
    this.pollingTimer = null;
  };

  detectChangesPoll = async () => {
    const {id} = this.state.instance;
    const {treeRowIds} = this.state.selection;
    const shouldFetchVariables = treeRowIds.length === 1;

    let requestsPromises = [
      api.fetchWorkflowInstance(id),
      api.fetchWorkflowInstanceIncidents(id),
      fetchActivityInstancesTree(id),
      fetchEvents(id)
    ];

    if (shouldFetchVariables) {
      requestsPromises.push(api.fetchVariables(id, treeRowIds[0]));
    }

    const [
      instance,
      incidents,
      activitiesInstancesTree,
      events,
      variables
    ] = await Promise.all(requestsPromises);

    const activityIdToActivityInstanceMap = getActivityIdToActivityInstancesMap(
      activitiesInstancesTree
    );

    this.setState(
      {
        instance,
        incidents,
        events,
        activityInstancesTree: {
          ...activitiesInstancesTree,
          id: instance.id,
          type: 'WORKFLOW',
          state: instance.state
        },
        activityIdToActivityInstanceMap,
        ...(shouldFetchVariables && variables),
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
    const {activityInstanceId, metadata} = events.reduce(
      (acc, event) =>
        event.activityInstanceId === treeRowIds[0] ? event : acc,
      null
    );

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

  formatIncidents = () => {
    if (this.state.instance.state !== 'INCIDENT') {
      return [];
    }

    return this.state.incidents.incidents.map(item => {
      const clone = Object.assign({}, item);
      clone.flowNodeName =
        this.state.activityIdToNameMap.get(item.flowNodeId) || UNNAMED_ACTIVITY;

      return clone;
    });
  };

  formatFlowNodes = () => {
    return this.state.incidents.flowNodes.map(item => {
      const clone = Object.assign({}, item);
      clone.flowNodeName =
        this.state.activityIdToNameMap.get(item.flowNodeId) || UNNAMED_ACTIVITY;

      return clone;
    });
  };

  handleIncidentOperation = () => {
    this.setState({forceInstanceSpinner: true});
  };

  handleInstanceOperation = () => {
    this.setState({forceIncidentsSpinner: true});
  };

  render() {
    const {
      loaded,
      diagramDefinitions,
      instance,
      selection,
      activityIdToActivityInstanceMap,
      activityIdToNameMap,
      variables
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
                  incidents={this.formatIncidents()}
                  incidentsCount={this.state.incidents.count}
                  instance={this.state.instance}
                  forceSpinner={this.state.forceIncidentsSpinner}
                  selectedIncidents={this.state.selection.treeRowIds}
                  onIncidentOperation={this.handleIncidentOperation}
                  onIncidentSelection={this.handleTreeRowSelection}
                  flowNodes={this.formatFlowNodes()}
                  errorTypes={this.state.incidents.errorTypes}
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
              <Variables variables={variables} />
            </InstanceHistory>
          </SplitPane>
        </Styled.Instance>
      </Fragment>
    );
  }
}
