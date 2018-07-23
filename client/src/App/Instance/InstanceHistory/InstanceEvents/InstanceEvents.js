import React from 'react';
import PropTypes from 'prop-types';

import {fetchEvents} from 'modules/api/events';

import ExpansionPanel from './ExpansionPanel';
import {getGroupedEvents} from './service';
import * as Styled from './styled';

export default class InstanceEvents extends React.Component {
  static propTypes = {
    instance: PropTypes.shape({
      id: PropTypes.string.isRequired
    }).isRequired,
    activitiesDetails: PropTypes.array
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

    if (!activitiesDetails || !events) {
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

  renderEvent = ({eventType}, idx) => {
    const key = `${eventType}${idx}`;

    return (
      <ExpansionPanel key={key}>
        <ExpansionPanel.Summary>{eventType}</ExpansionPanel.Summary>
        <ExpansionPanel.Details>META DATA</ExpansionPanel.Details>
      </ExpansionPanel>
    );
  };

  renderEventsGroup = ({name, events}, idx) => {
    const key = `${name}${idx}`;

    return (
      <ExpansionPanel key={key}>
        <ExpansionPanel.Summary bold={Boolean(events)}>
          {name}
        </ExpansionPanel.Summary>
        <ExpansionPanel.Details>
          {events.map(this.renderEvent)}
        </ExpansionPanel.Details>
      </ExpansionPanel>
    );
  };

  renderGroup = (group, idx) => {
    return group.events
      ? this.renderEventsGroup(group, idx)
      : this.renderEvent(group, idx);
  };

  render() {
    return (
      <Styled.InstanceEvents {...this.props}>
        <Styled.EventsContainer>
          {!this.state.groupedEvents
            ? null
            : this.state.groupedEvents.map(this.renderGroup)}
        </Styled.EventsContainer>
      </Styled.InstanceEvents>
    );
  }
}
