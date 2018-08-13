import React, {Component, Fragment} from 'react';
import PropTypes from 'prop-types';

import Content from 'modules/components/Content';
import SplitPane from 'modules/components/SplitPane';
import * as api from 'modules/api/instances';
import {getActiveIncident} from 'modules/utils/instance';
import {ACTIVITY_STATE} from 'modules/constants';

import InstanceDetail from './InstanceDetail';
import Header from '../Header';
import DiagramPanel from './DiagramPanel';
import InstanceHistory from './InstanceHistory';
import * as Styled from './styled';

const {ACTIVE, INCIDENT} = ACTIVITY_STATE;

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

  onFlowNodesDetailsReady = flowNodesDetails => {
    const {instance} = this.state;
    const {activityInstanceId} = getActiveIncident(instance.incidents) || {};

    const activitiesDetails = instance.activities.reduce(
      (map, {id, ...activity}) => {
        // change activity state to incident in case the activity is active
        // and it has an active incident
        if (activity.state === ACTIVE && activityInstanceId === id) {
          activity.state = INCIDENT;
        }

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

  render() {
    if (!this.state.loaded) {
      return 'Loading';
    }

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
                onFlowNodesDetailsReady={this.onFlowNodesDetailsReady}
              />
              <InstanceHistory
                instance={this.state.instance}
                activitiesDetails={this.state.activitiesDetails}
              />
            </SplitPane>
          </Styled.Instance>
        </Content>
      </Fragment>
    );
  }
}
