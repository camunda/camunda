import React from 'react';
import PropTypes from 'prop-types';

import {isEmpty} from 'modules/utils';
import {ACTIVITY_STATE, EVENT_TYPE, EVENT_SOURCE_TYPE} from 'modules/constants';

import Foldable from './Foldable';
import * as Styled from './styled';

export default class InstanceEvents extends React.Component {
  static propTypes = {
    groupedEvents: PropTypes.array.isRequired,
    onEventRowChanged: PropTypes.func.isRequired,
    selectedEventRow: PropTypes.object
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
  renderFoldableDetails = ({indentation, details}) => {
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
                indentation={indentation}
              >{`${key}: ${value}`}</Styled.DataEntry>
            );
          }

          // if the value is of type object, render a Foldable
          return (
            <Foldable key={idx} indentation={indentation}>
              <Foldable.Summary>{key}</Foldable.Summary>
              <Foldable.Details>
                {this.renderFoldableDetails({
                  details: value,
                  indentation: indentation + 1
                })}
              </Foldable.Details>
            </Foldable>
          );
        })
    );
  };

  renderEvent = (
    {label, eventType, eventSourceType, metadata, indentation, payload},
    idx
  ) => {
    const key = `${eventType}${idx}`;
    const isOpenIncidentEvent =
      eventType === EVENT_TYPE.CREATED &&
      eventSourceType === EVENT_SOURCE_TYPE.INCIDENT;

    return (
      <Foldable key={key} indentation={indentation}>
        <Foldable.Summary
          isFoldable={!isEmpty(metadata)}
          isOpenIncidentEvent={isOpenIncidentEvent}
          isSelected={key === this.props.selectedEventRow.key}
          onSelection={() => this.props.onEventRowChanged({key, payload})}
          data-test={key}
        >
          {!isOpenIncidentEvent ? '' : <Styled.IncidentIcon />}
          {label}
        </Foldable.Summary>
        {!!metadata && (
          <Foldable.Details>
            {this.renderFoldableDetails({
              details: metadata,
              indentation: indentation + 1
            })}
          </Foldable.Details>
        )}
      </Foldable>
    );
  };

  renderWorkflowInstanceEvent = (data, idx) => {
    return this.renderEvent({...data, indentation: 0}, idx);
  };

  renderActivityEvent = (data, idx) => {
    return this.renderEvent({...data, indentation: 1}, idx);
  };

  renderActivityEvents = ({name, events, state}, idx) => {
    const key = `${name}${idx}`;

    return (
      <Foldable key={key}>
        <Foldable.Summary
          isSelected={key === this.props.selectedEventRow.key}
          onSelection={() => this.props.onEventRowChanged({key, payload: null})}
          data-test={key}
        >
          {name}
          {state !== ACTIVITY_STATE.INCIDENT ? (
            ''
          ) : (
            <Styled.IncidentIcon title={`${name} has an incident`} />
          )}
        </Foldable.Summary>
        <Foldable.Details>
          {events.map(this.renderActivityEvent)}
        </Foldable.Details>
      </Foldable>
    );
  };

  renderGroup = (group, idx) => {
    return (
      <Styled.EventEntry key={idx}>
        {group.events
          ? this.renderActivityEvents(group, idx)
          : this.renderWorkflowInstanceEvent(group, idx)}
      </Styled.EventEntry>
    );
  };

  render() {
    return (
      <Styled.InstanceEvents {...this.props}>
        <Styled.EventsContainer>
          {this.props.groupedEvents.map(this.renderGroup)}
        </Styled.EventsContainer>
      </Styled.InstanceEvents>
    );
  }
}
