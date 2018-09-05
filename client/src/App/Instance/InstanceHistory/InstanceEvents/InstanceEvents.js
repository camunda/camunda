import React from 'react';
import PropTypes from 'prop-types';

import {isEmpty} from 'modules/utils';
import {ACTIVITY_STATE, EVENT_TYPE, EVENT_SOURCE_TYPE} from 'modules/constants';

import Foldable from './Foldable';
import * as Styled from './styled';

export default class InstanceEvents extends React.Component {
  static propTypes = {
    groupedEvents: PropTypes.array
  };

  /**
   * Filters foldable details and renders them.
   * @param {object} details: An object of key-value entries where the value can be a string | number | object | null.
   * e.g. {
   *  jobCustomHeaders: {}
      jobDeadline: "2018-08-31T07:16:52.805+0000"
      jobId: "8590888008"
      jobRetries: 3
   * }
   */
  renderFoldableDetails = details => {
    return (
      Object.entries(details)
        // filter out empty primitives and objects
        .filter(([_, value]) => {
          if (typeof value === 'object') {
            return !isEmpty(value);
          }

          return Boolean(value);
        })
        .map(([key, value], idx) => {
          // if the value is a primitive, render a DataEntry
          if (typeof value !== 'object') {
            return (
              <Styled.DataEntry
                key={idx}
              >{`${key}: ${value}`}</Styled.DataEntry>
            );
          }

          // if the value is of type object, render a Foldable
          return (
            <Foldable key={idx}>
              <Foldable.Summary>{key}</Foldable.Summary>
              <Foldable.Details>
                {this.renderFoldableDetails(value)}
              </Foldable.Details>
            </Foldable>
          );
        })
    );
  };

  renderEvent = ({eventType, eventSourceType, metadata}, idx) => {
    const key = `${eventType}${idx}`;
    const isOpenIncidentEvent =
      eventType === EVENT_TYPE.CREATED &&
      eventSourceType === EVENT_SOURCE_TYPE.INCIDENT;

    return (
      <Foldable key={key}>
        <Styled.EventFoldableSummary
          isFoldable={!isEmpty(metadata)}
          isOpenIncidentEvent={isOpenIncidentEvent}
        >
          {!isOpenIncidentEvent ? '' : <Styled.IncidentIcon />}
          {eventType}
        </Styled.EventFoldableSummary>
        <Foldable.Details>
          {!!metadata && this.renderFoldableDetails(metadata)}
        </Foldable.Details>
      </Foldable>
    );
  };

  renderEventsGroup = ({name, events, state}, idx) => {
    const key = `${name}${idx}`;

    return (
      <Foldable key={key}>
        <Styled.GroupFoldableSummary>
          {name}
          {state !== ACTIVITY_STATE.INCIDENT ? '' : <Styled.IncidentIcon />}
        </Styled.GroupFoldableSummary>
        <Foldable.Details>{events.map(this.renderEvent)}</Foldable.Details>
      </Foldable>
    );
  };

  renderGroup = (group, idx) => {
    return (
      <Styled.EventEntry key={idx}>
        {group.events
          ? this.renderEventsGroup(group, idx)
          : this.renderEvent(group, idx)}
      </Styled.EventEntry>
    );
  };

  render() {
    return (
      <Styled.InstanceEvents {...this.props}>
        <Styled.EventsContainer>
          {this.props.groupedEvents
            ? this.props.groupedEvents.map(this.renderGroup)
            : null}
        </Styled.EventsContainer>
      </Styled.InstanceEvents>
    );
  }
}
