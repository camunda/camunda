import React from 'react';
import PropTypes from 'prop-types';

import SplitPane from 'modules/components/SplitPane';
import Copyright from 'modules/components/Copyright';
import {fetchEvents} from 'modules/api/events';

import InstanceLog from './InstanceLog';
import InstanceEvents from './InstanceEvents';
import InstancePayload from './InstancePayload';
import {getGroupedEvents, getActivityInstanceEvents} from './service';
import {isEmpty} from 'modules/utils';
import * as Styled from './styled';

export default class InstanceHistory extends React.Component {
  static propTypes = {
    instance: PropTypes.object.isRequired,
    activitiesDetails: PropTypes.object,
    selectedActivityInstanceId: PropTypes.string,
    onActivityInstanceSelected: PropTypes.func
  };

  state = {
    events: null,
    groupedEvents: null,
    selectedEventRow: {
      key: null,
      payload: null
    }
  };

  async componentDidMount() {
    const events = await fetchEvents(this.props.instance.id);
    this.setState({events});
  }

  componentDidUpdate(prevProps, prevState) {
    const {activitiesDetails, selectedActivityInstanceId} = this.props;
    const {events} = this.state;

    if (isEmpty(activitiesDetails) || !events) {
      return;
    }

    const haveActivitiesDetailsChanged =
      activitiesDetails !== prevProps.activitiesDetails;
    const haveEventsChanged = events !== prevState.events;
    const hasSelectedActivityInstanceIdChanged =
      selectedActivityInstanceId !== prevProps.selectedActivityInstanceId;

    if (haveActivitiesDetailsChanged || haveEventsChanged) {
      const groupedEvents = getGroupedEvents({
        events,
        activitiesDetails
      });
      return this.setState({groupedEvents});
    }

    // reset the event row selection whenever activity changes
    // whenever the selected activity instance id changes.
    if (hasSelectedActivityInstanceIdChanged) {
      this.resetEventRowChange();
    }
  }

  resetEventRowChange = () => {
    this.setState({
      selectedEventRow: {key: null, payload: null}
    });
  };

  handleEventRowChange = ({key, payload}) => {
    // if we selected the same row again, reset the selected event row
    if (key === this.state.selectedEventRow.key) {
      return this.resetEventRowChange();
    }

    let newPayload;
    try {
      newPayload = JSON.parse(payload);
    } catch (e) {
      newPayload = null;
    }

    this.setState({selectedEventRow: {key, payload: newPayload}});
  };

  render() {
    const {selectedActivityInstanceId} = this.props;

    let filteredGroupedEvents = this.state.groupedEvents;

    // if there is a selected activity instane, only show events
    // corresponding to it
    if (selectedActivityInstanceId && this.state.events) {
      filteredGroupedEvents = getActivityInstanceEvents({
        activityInstanceId: selectedActivityInstanceId,
        events: this.state.events
      });
    }

    return (
      <SplitPane.Pane {...this.props}>
        <SplitPane.Pane.Header>Instance History</SplitPane.Pane.Header>
        <Styled.PaneBody>
          <InstanceLog
            instance={this.props.instance}
            activitiesDetails={this.props.activitiesDetails}
            selectedActivityInstanceId={this.props.selectedActivityInstanceId}
            onActivityInstanceSelected={this.props.onActivityInstanceSelected}
          />
          <InstanceEvents
            groupedEvents={filteredGroupedEvents}
            onEventRowChanged={this.handleEventRowChange}
            selectedEventRow={this.state.selectedEventRow}
          />
          <InstancePayload payload={this.state.selectedEventRow.payload} />
        </Styled.PaneBody>
        <Styled.PaneFooter>
          <Copyright />
        </Styled.PaneFooter>
      </SplitPane.Pane>
    );
  }
}
