import React, {Component, Fragment} from 'react';
import PropTypes from 'prop-types';
import {isEqual} from 'lodash';

import SplitPane from 'modules/components/SplitPane';
import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';
import Diagram from 'modules/components/Diagram';
import {PAGE_TITLE} from 'modules/constants';
import {getWorkflowName} from 'modules/utils/instance';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {compactObject} from 'modules/utils';
import {formatDate} from 'modules/utils/date';
import * as api from 'modules/api/instances';
import {fetchWorkflowXML} from 'modules/api/diagram';
import {fetchEvents} from 'modules/api/events';

import InstanceDetail from './InstanceDetail';
import Header from '../Header';
import DiagramPanel from './DiagramPanel';
import InstanceHistory from './InstanceHistory';
import {
  getFlowNodeStateOverlays,
  getFlowNodesDetails,
  isRunningInstance
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
        activityInstanceId: null,
        flowNodeId: null
      },
      diagramModel: {},
      loaded: false,
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

    const [diagramXml, events] = await Promise.all([
      fetchWorkflowXML(instance.workflowId),
      fetchEvents(instance.id)
    ]);
    const diagramModel = await parseDiagramXML(diagramXml);

    this.setState(
      {
        loaded: true,
        instance,
        diagramModel,
        events
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
   * Converts a bpmn elements object to a map of activities details,
   * in the following shape: activityId -> details.
   * @param {object} elements: bpmn elements
   */

  elementsToActivitiesDetails = elements => {
    const {instance} = this.state;
    const flowNodesDetails = getFlowNodesDetails(elements);

    return instance.activities.reduce((map, {id, ...activity}) => {
      // ignore activities that don't have mathincg flow node details
      // e.g. sub process
      if (!flowNodesDetails[activity.activityId]) {
        return map;
      }

      return {
        ...map,
        [id]: {
          ...activity,
          ...flowNodesDetails[activity.activityId],
          id
        }
      };
    }, {});
  };

  /**
   * Handles selecting an activity instance in the instance log
   * @param {string} activityInstanceId: id of the selected activiy instance
   */
  handleActivityInstanceSelection = activityInstanceId => {
    // get the first flow node id (i.e. activity id) corresponding to the flowNodeId
    const flowNodeId = !activityInstanceId
      ? null
      : this.state.instance.activities.find(
          activity => activity.id === activityInstanceId
        ).activityId;

    this.setState({
      selection: {
        activityInstanceId,
        flowNodeId
      }
    });
  };

  /**
   * Handles selecting a flow node from the diagram
   * @param {string} flowNodeId: id of the selected flow node
   */
  handleFlowNodeSelection = flowNodeId => {
    // get the first activity instance corresponding to the flowNodeId
    const activityInstanceId = !flowNodeId
      ? null
      : this.state.instance.activities.find(
          activity => activity.activityId === flowNodeId
        ).id;

    this.setState({
      selection: {
        activityInstanceId,
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
    const {id, state, activities, operations} = this.state.instance;
    const newInstance = await api.fetchWorkflowInstance(id);
    const hasInstanceChanged =
      newInstance.state !== state ||
      !isEqual(newInstance.activities, activities) ||
      !isEqual(newInstance.operations, operations);

    hasInstanceChanged
      ? this.setState({instance: newInstance}, () => {
          this.initializePolling();
        })
      : this.initializePolling();
  };

  getMetadataFromaActivitiesDetails = activitiesDetails => {
    const {
      selection: {flowNodeId},
      events
    } = this.state;

    if (!flowNodeId) {
      return null;
    }

    const {activityInstanceId, metadata} = events.find(
      event => event.activityId === flowNodeId && event.activityInstanceId
    );

    const {jobId} = metadata || {};
    const {startDate, endDate} = activitiesDetails[activityInstanceId];

    return compactObject({
      'Flow Node Instance Id': activityInstanceId,
      'Job Id': jobId,
      Started: formatDate(startDate),
      Completed: formatDate(endDate)
    });
  };

  render() {
    const {loaded, diagramModel, instance, selection, events} = this.state;

    if (!loaded) {
      return 'Loading';
    }

    const activitiesDetails = this.elementsToActivitiesDetails(
      diagramModel.bpmnElements
    );

    // Get extra information for the diagram
    const selectableFlowNodes = Object.values(activitiesDetails).map(
      activity => activity.activityId
    );

    const flowNodeStateOverlays = getFlowNodeStateOverlays(activitiesDetails);

    const metadata = this.getMetadataFromaActivitiesDetails(activitiesDetails);

    return (
      <Fragment>
        <Header detail={<InstanceDetail instance={instance} />} />
        <Styled.Instance>
          <VisuallyHiddenH1>
            {`Camunda Operate Instance ${this.state.instance.id}`}
          </VisuallyHiddenH1>
          <SplitPane titles={{top: 'Workflow', bottom: 'Instance Details'}}>
            <DiagramPanel instance={instance}>
              {diagramModel && (
                <Diagram
                  onFlowNodeSelected={this.handleFlowNodeSelection}
                  selectableFlowNodes={selectableFlowNodes}
                  selectedFlowNode={selection.flowNodeId}
                  flowNodeStateOverlays={flowNodeStateOverlays}
                  definitions={diagramModel.definitions}
                  metadata={metadata}
                />
              )}
            </DiagramPanel>
            <InstanceHistory
              instance={instance}
              activitiesDetails={activitiesDetails}
              selectedActivityInstanceId={selection.activityInstanceId}
              onActivityInstanceSelected={this.handleActivityInstanceSelection}
              events={events}
            />
          </SplitPane>
        </Styled.Instance>
      </Fragment>
    );
  }
}
