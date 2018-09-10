import React from 'react';
import PropTypes from 'prop-types';

import SplitPane from 'modules/components/SplitPane';
import Copyright from 'modules/components/Copyright';
import {fetchEvents} from 'modules/api/events';

import InstanceLog from './InstanceLog';
import InstanceEvents from './InstanceEvents';
import {getGroupedEvents, getEventsBySelectedActivityId} from './service';
import {isEmpty} from 'modules/utils';
import * as Styled from './styled';

export default class InstanceHistory extends React.Component {
  static propTypes = {
    instance: PropTypes.object.isRequired,
    activitiesDetails: PropTypes.object,
    selectedActivityId: PropTypes.string,
    onActivitySelected: PropTypes.func
  };

  state = {
    events: null,
    groupedEvents: null
  };

  async componentDidMount() {
    const events = await fetchEvents(this.props.instance.id);
    this.setState({events});
  }

  componentDidUpdate(prevProps, prevState) {
    const {activitiesDetails} = this.props;
    const {events} = this.state;

    if (isEmpty(activitiesDetails) || !events) {
      return;
    }

    const haveActivitiesDetailsChanged =
      activitiesDetails !== prevProps.activitiesDetails;
    const haveEventsChanged = events !== prevState.events;

    if (haveActivitiesDetailsChanged || haveEventsChanged) {
      const groupedEvents = getGroupedEvents({
        events: this.state.events,
        activitiesDetails
      });

      this.setState({groupedEvents});
    }
  }

  render() {
    const filteredGroupedEvents = !this.state.groupedEvents
      ? null
      : getEventsBySelectedActivityId({
          selectedActivityId: this.props.selectedActivityId,
          groupedEvents: this.state.groupedEvents
        });

    return (
      <SplitPane.Pane {...this.props}>
        <SplitPane.Pane.Header>Instance History</SplitPane.Pane.Header>
        <Styled.PaneBody>
          <InstanceLog
            instance={this.props.instance}
            activitiesDetails={this.props.activitiesDetails}
            selectedActivityId={this.props.selectedActivityId}
            onActivitySelected={this.props.onActivitySelected}
          />
          <InstanceEvents groupedEvents={filteredGroupedEvents} />
          <Styled.Section>C</Styled.Section>
        </Styled.PaneBody>
        <Styled.PaneFooter>
          <Copyright />
        </Styled.PaneFooter>
      </SplitPane.Pane>
    );
  }
}
