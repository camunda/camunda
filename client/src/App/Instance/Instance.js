import React, {Component, Fragment} from 'react';
import PropTypes from 'prop-types';

import SplitPane from 'modules/components/SplitPane';
import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';
import Diagram from 'modules/components/Diagram';
import {PAGE_TITLE} from 'modules/constants';
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
      events: []
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

    const [activitiesInstancesTree, diagramXml, events] = await Promise.all([
      fetchActivityInstancesTree(id),
      fetchWorkflowXML(instance.workflowId),
      fetchEvents(instance.id)
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
        activityIdToNameMap,
        diagramDefinitions: definitions,
        events,
        activityInstancesTree: {
          ...activitiesInstancesTree,
          id: instance.id,
          type: 'WORKFLOW',
          state: instance.state
        },
        activityIdToActivityInstanceMap,
        selection: {
          flowNodeId: null,
          treeRowIds: [instance.id]
        }
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
  handleTreeRowSelection = node => {
    const {selection, instance} = this.state;
    const isRootNode = node.id === instance.id;
    // get the first flow node id (i.e. activity id) corresponding to the flowNodeId
    const flowNodeId = isRootNode ? null : node.activityId;
    const hasSiblings = selection.treeRowIds.length > 1;
    const rowIsSelected = !!selection.treeRowIds.find(
      selectedId => selectedId === node.id
    );

    this.setState({
      selection: {
        flowNodeId: rowIsSelected ? null : flowNodeId,
        treeRowIds: rowIsSelected && !hasSiblings ? [instance.id] : [node.id]
      }
    });
  };

  /**
   * Handles selecting a flow node from the diagram
   * @param {string} flowNodeId: id of the selected flow node
   */
  handleFlowNodeSelection = flowNodeId => {
    const {instance, activityIdToActivityInstanceMap} = this.state;

    // get the first activity instance corresponding to the flowNodeId
    const treeRowIds = !flowNodeId
      ? [instance.id]
      : [...activityIdToActivityInstanceMap.get(flowNodeId).keys()];

    this.setState({
      selection: {
        treeRowIds,
        flowNodeId
      }
    });
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
    const [instance, activitiesInstancesTree, events] = await Promise.all([
      api.fetchWorkflowInstance(id),
      fetchActivityInstancesTree(id),
      fetchEvents(id)
    ]);

    const activityIdToActivityInstanceMap = getActivityIdToActivityInstancesMap(
      activitiesInstancesTree
    );

    this.setState(
      {
        instance,
        events,
        activityInstancesTree: {
          ...activitiesInstancesTree,
          id: instance.id,
          type: 'WORKFLOW',
          state: instance.state
        },
        activityIdToActivityInstanceMap
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

    if (treeRowIds.length > 1) {
      return {};
    }

    // get the last event corresponding to the given flowNodeId (= activityId)
    const {activityInstanceId, metadata} = events.reduce(
      (acc, event) =>
        event.activityInstanceId === treeRowIds[0] ? event : acc,
      null
    );

    // get corresponding start and end dates
    const {startDate, endDate} = activityIdToActivityInstanceMap
      .get(flowNodeId)
      .get(activityInstanceId);

    // return a cleaned-up and beautified metadata object
    return Object.entries({
      ...metadata,
      activityInstanceId,
      startDate,
      endDate
    }).reduce((cleanMetadata, [key, value]) => {
      if (['startDate', 'endDate'].includes(key)) {
        return {...cleanMetadata, [key]: formatDate(value)};
      }

      // ignore other empty values
      if (!value) {
        return cleanMetadata;
      }

      return {...cleanMetadata, [key]: value};
    }, {});
  };

  getNodeWithName = node => {
    const {instance} = this.state;

    const name =
      node.id === instance.id
        ? getWorkflowName(instance)
        : this.state.activityIdToNameMap.get(node.activityId);

    return {
      ...node,
      name
    };
  };

  render() {
    const {
      loaded,
      diagramDefinitions,
      instance,
      selection,
      activityIdToActivityInstanceMap
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

    return (
      <Fragment>
        <Header detail={<InstanceDetail instance={instance} />} />
        <Styled.Instance>
          <VisuallyHiddenH1>
            {`Camunda Operate Instance ${this.state.instance.id}`}
          </VisuallyHiddenH1>
          <SplitPane titles={{top: 'Workflow', bottom: 'Instance Details'}}>
            <DiagramPanel instance={instance}>
              {diagramDefinitions && (
                <Diagram
                  onFlowNodeSelection={this.handleFlowNodeSelection}
                  selectableFlowNodes={selectableFlowNodes}
                  selectedFlowNodeId={selection.flowNodeId}
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
              <div style={{color: 'white'}}>Payload</div>
            </InstanceHistory>
          </SplitPane>
        </Styled.Instance>
      </Fragment>
    );
  }
}
