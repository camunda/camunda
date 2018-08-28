import React, {Component, Fragment} from 'react';
import PropTypes from 'prop-types';

import Content from 'modules/components/Content';
import SplitPane from 'modules/components/SplitPane';
import * as api from 'modules/api/instances';

import InstanceDetail from './InstanceDetail';
import Header from '../Header';
import DiagramPanel from './DiagramPanel';
import InstanceHistory from './InstanceHistory';
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
    activitiesDetails: null,
    selectedActivityId: null,
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

  handleActivitySelection = selectedActivityId => {
    this.setState({selectedActivityId});
  };

  render() {
    if (!this.state.loaded) {
      return 'Loading';
    }

    const instanceActivitiesIds = (
      (this.state.instance || {}).activities || []
    ).map(({activityId}) => activityId);

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
                selectableFlowNodes={instanceActivitiesIds}
                selectedFlowNode={this.state.selectedActivityId}
                onFlowNodeSelected={this.handleActivitySelection}
              />
              <InstanceHistory
                instance={this.state.instance}
                activitiesDetails={this.state.activitiesDetails}
                selectedActivityId={this.state.selectedActivityId}
                onActivitySelected={this.handleActivitySelection}
              />
            </SplitPane>
          </Styled.Instance>
        </Content>
      </Fragment>
    );
  }
}
