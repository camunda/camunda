import React, {Component, Fragment} from 'react';
import PropTypes from 'prop-types';

import SplitPane from 'modules/components/SplitPane';
import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';
import Diagram from 'modules/components/Diagram';
import * as api from 'modules/api/instances';
import {fetchWorkflowXML} from 'modules/api/diagram';
import {PAGE_TITLE} from 'modules/constants';
import {getWorkflowName} from 'modules/utils/instance';
import {parseDiagramXML} from 'modules/utils/bpmn';

import InstanceDetail from './InstanceDetail';
import Header from '../Header';
import DiagramPanel from './DiagramPanel';
import InstanceHistory from './InstanceHistory';
import {getFlowNodeStateOverlays, getFlowNodesDetails} from './service';
import * as Styled from './styled';

export default class Instance extends Component {
  static propTypes = {
    match: PropTypes.shape({
      params: PropTypes.shape({
        id: PropTypes.string.isRequired
      }).isRequired
    }).isRequired
  };

  state = {
    instance: null,
    selection: {
      activityInstanceId: null,
      flowNodeId: null
    },
    diagramModel: {},
    loaded: false
  };

  async componentDidMount() {
    const id = this.props.match.params.id;
    const instance = await api.fetchWorkflowInstance(id);

    document.title = PAGE_TITLE.INSTANCE(
      instance.id,
      getWorkflowName(instance)
    );

    const diagramXml = await fetchWorkflowXML(instance.workflowId);
    const diagramModel = await parseDiagramXML(diagramXml);

    this.setState({
      loaded: true,
      instance,
      diagramModel
    });
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

  render() {
    const {loaded, diagramModel, instance, selection} = this.state;

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
                  onFlowNodesDetailsReady={this.handleFlowNodesDetailsReady}
                  selectableFlowNodes={selectableFlowNodes}
                  selectedFlowNode={selection.flowNodeId}
                  onFlowNodeSelected={this.handleFlowNodeSelection}
                  flowNodeStateOverlays={flowNodeStateOverlays}
                  definitions={diagramModel.definitions}
                />
              )}
            </DiagramPanel>
            <InstanceHistory
              instance={instance}
              activitiesDetails={activitiesDetails}
              selectedActivityInstanceId={selection.activityInstanceId}
              onActivityInstanceSelected={this.handleActivityInstanceSelection}
            />
          </SplitPane>
        </Styled.Instance>
      </Fragment>
    );
  }
}
