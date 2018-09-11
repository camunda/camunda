import React, {Component, Fragment} from 'react';
import PropTypes from 'prop-types';

import Content from 'modules/components/Content';
import SplitPane from 'modules/components/SplitPane';
import * as api from 'modules/api/instances';

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

    this.setState({
      loaded: true,
      instance
    });
  }

  handleFlowNodesDetailsReady = flowNodesDetails => {
    const {instance} = this.state;
    const activitiesDetails = instance.activities.reduce(
      (map, {id, ...activity}) => {
        return {
          ...map,
          [id]: {
            ...activity,
            ...flowNodesDetails[activity.activityId]
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
    const selectableFlowNodes = (this.state.instance || {}).activities.map(
      ({activityId}) => activityId
    );
    const flowNodeStateOverlays = getFlowNodeStateOverlays(
      this.state.activitiesDetails
    );

    return (
      <Fragment>
        <Header
          instances={14576}
          filters={9263}
          selections={24}
          incidents={328}
          detail={<InstanceDetail instance={this.state.instance} />}
        />
        <Content>
          <Styled.Instance>
            <SplitPane>
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
                onActivityInstanceSelected={
                  this.handleActivityInstanceSelection
                }
              />
            </SplitPane>
          </Styled.Instance>
        </Content>
      </Fragment>
    );
  }
}
