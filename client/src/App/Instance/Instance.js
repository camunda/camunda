import React, {Component, Fragment} from 'react';
import PropTypes from 'prop-types';

import SplitPane from 'modules/components/SplitPane';
import TransparentHeading from 'modules/components/TransparentHeading';
import * as api from 'modules/api/instances';
import {PAGE_TITLE} from 'modules/constants';
import {getWorkflowName} from 'modules/utils/instance';

import InstanceDetail from './InstanceDetail';
import Header from '../Header';
import DiagramPanel from './DiagramPanel';
import InstanceHistory from './InstanceHistory';
import {getFlowNodeStateOverlays} from './service';
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
    activitiesDetails: {},
    selection: {
      activityInstanceId: null,
      flowNodeId: null
    },
    loaded: false
  };

  async componentDidMount() {
    const id = this.props.match.params.id;
    const instance = await api.fetchWorkflowInstance(id);

    document.title = PAGE_TITLE.INSTANCE(
      instance.id,
      getWorkflowName(instance)
    );

    this.setState({
      loaded: true,
      instance
    });
  }

  handleFlowNodesDetailsReady = flowNodesDetails => {
    const {instance} = this.state;
    const activitiesDetails = instance.activities.reduce(
      (map, {id, ...activity}) => {
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
      },
      {}
    );

    this.setState({activitiesDetails});
  };

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
    if (!this.state.loaded) {
      return 'Loading';
    }

    // Get extra information for the diagram
    const selectableFlowNodes = Object.values(this.state.activitiesDetails).map(
      activity => activity.activityId
    );

    const flowNodeStateOverlays = getFlowNodeStateOverlays(
      this.state.activitiesDetails
    );

    return (
      <Fragment>
        <TransparentHeading>
          {`Camunda Operate Instance ${this.state.instance.id}`}
        </TransparentHeading>
        <Header detail={<InstanceDetail instance={this.state.instance} />} />
        <Styled.Instance>
          <SplitPane titles={{top: 'Workflow', bottom: 'Instance Details'}}>
            <DiagramPanel
              instance={this.state.instance}
              onFlowNodesDetailsReady={this.handleFlowNodesDetailsReady}
              selectableFlowNodes={selectableFlowNodes}
              selectedFlowNode={this.state.selection.flowNodeId}
              onFlowNodeSelected={this.handleFlowNodeSelection}
              flowNodeStateOverlays={flowNodeStateOverlays}
            />
            <InstanceHistory
              instance={this.state.instance}
              activitiesDetails={this.state.activitiesDetails}
              selectedActivityInstanceId={
                this.state.selection.activityInstanceId
              }
              onActivityInstanceSelected={this.handleActivityInstanceSelection}
            />
          </SplitPane>
        </Styled.Instance>
      </Fragment>
    );
  }
}
