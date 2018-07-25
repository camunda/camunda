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

  renderData = data => {
    return Object.entries(data)
      .filter(([_, value]) => Boolean(value))
      .map(([key, value], idx) => {
        if (typeof value !== 'object') {
          return (
            <Styled.DataEntry key={idx}>{`${key}: ${value}`}</Styled.DataEntry>
          );
        }

        return (
          <ExpansionPanel key={idx}>
            <ExpansionPanel.Summary>{key}</ExpansionPanel.Summary>
            <ExpansionPanel.Details>
              {!!value && this.renderData(value)}
            </ExpansionPanel.Details>
          </ExpansionPanel>
        );
      });
  };

  renderEvent = ({eventType, metadata}, idx) => {
    const key = `${eventType}${idx}`;

    return (
      <ExpansionPanel key={key}>
        <ExpansionPanel.Summary>{eventType}</ExpansionPanel.Summary>
        <ExpansionPanel.Details>
          {!!metadata && this.renderData(metadata)}
        </ExpansionPanel.Details>
      </ExpansionPanel>
    );
  };

  renderEventsGroup = ({name, events}, idx) => {
    const key = `${name}${idx}`;

    return (
      <ExpansionPanel key={key}>
        <ExpansionPanel.Summary bold={true}>{name}</ExpansionPanel.Summary>
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
          {this.state.groupedEvents
            ? this.state.groupedEvents.map(this.renderGroup)
            : null}
        </Styled.EventsContainer>
      </Styled.InstanceEvents>
    );
  }
}
