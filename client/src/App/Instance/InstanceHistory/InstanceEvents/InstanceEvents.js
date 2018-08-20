import React from 'react';
import PropTypes from 'prop-types';

import {isEmpty} from 'modules/utils';

import Foldable from './Foldable';
import * as Styled from './styled';

export default class InstanceEvents extends React.Component {
  static propTypes = {
    groupedEvents: PropTypes.array
  };

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
