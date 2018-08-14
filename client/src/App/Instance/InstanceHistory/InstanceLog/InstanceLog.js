import React from 'react';
import PropTypes from 'prop-types';

import {getWorkflowName} from 'modules/utils/instance';
import {HEADER} from 'modules/constants';

import * as Styled from './styled';

export default class InstanceLog extends React.Component {
  static propTypes = {
    instance: PropTypes.object,
    activitiesDetails: PropTypes.shape({
      id: PropTypes.string,
      state: PropTypes.string,
      type: PropTypes.string,
      name: PropTypes.string
    }),
    selectedLogEntry: PropTypes.string,
    handleSelectedLogEntry: PropTypes.func.isRequired
  };

  state = {
    selected: HEADER
  };

  renderLogEntry = ([id, {state, type, name}]) => (
    <Styled.LogEntry key={id}>
      <Styled.LogEntryToggle
        isSelected={this.props.selectedLogEntry === id}
        onClick={() => this.props.handleSelectedLogEntry(id)}
      >
        <Styled.FlowNodeIcon
          state={state}
          type={type}
          isSelected={this.props.selectedLogEntry === id}
        />
        {name}
      </Styled.LogEntryToggle>
    </Styled.LogEntry>
  );

  render() {
    const {instance, activitiesDetails} = this.props;

    return (
      <Styled.InstanceLog {...this.props}>
        <Styled.LogEntry>
          <Styled.HeaderToggle
            isSelected={this.props.selectedLogEntry === HEADER}
            onClick={() => this.props.handleSelectedLogEntry(HEADER)}
          >
            <Styled.DocumentIcon
              isSelected={this.props.selectedLogEntry === HEADER}
            />
            {getWorkflowName(instance)}
          </Styled.HeaderToggle>
        </Styled.LogEntry>
        {activitiesDetails
          ? Object.entries(activitiesDetails).map(this.renderLogEntry)
          : null}
      </Styled.InstanceLog>
    );
  }
}
