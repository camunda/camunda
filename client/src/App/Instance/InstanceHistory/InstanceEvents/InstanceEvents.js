import React from 'react';
import PropTypes from 'prop-types';

import {fetchEvents} from 'modules/api/events';
import {isEmpty} from 'modules/utils';

import Foldable from './Foldable';
import {getGroupedEvents} from './service';
import * as Styled from './styled';

export default class InstanceEvents extends React.Component {
  static propTypes = {
    instance: PropTypes.shape({
      id: PropTypes.string.isRequired
    }).isRequired,
    activitiesDetails: PropTypes.object
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
      .filter(([_, value]) => {
        if (typeof value === 'object') {
          return !isEmpty(value);
        }

        return Boolean(value);
      })
      .map(([key, value], idx) => {
        if (typeof value !== 'object') {
          return (
            <Styled.DataEntry key={idx}>{`${key}: ${value}`}</Styled.DataEntry>
          );
        }

        return (
          <Foldable key={idx}>
            <Foldable.Summary>{key}</Foldable.Summary>
            <Foldable.Details>{this.renderData(value)}</Foldable.Details>
          </Foldable>
        );
      });
  };

  renderEvent = ({eventType, metadata}, idx) => {
    const key = `${eventType}${idx}`;

    return (
      <Foldable key={key}>
        <Foldable.Summary isFoldable={!isEmpty(metadata)}>
          {eventType}
        </Foldable.Summary>
        <Foldable.Details>
          {!!metadata && this.renderData(metadata)}
        </Foldable.Details>
      </Foldable>
    );
  };

  renderEventsGroup = ({name, events}, idx) => {
    const key = `${name}${idx}`;

    return (
      <Foldable key={key}>
        <Foldable.Summary isBold={true}>{name}</Foldable.Summary>
        <Foldable.Details>{events.map(this.renderEvent)}</Foldable.Details>
      </Foldable>
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
