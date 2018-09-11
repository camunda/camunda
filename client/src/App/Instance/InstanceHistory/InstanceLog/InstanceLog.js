import React from 'react';
import PropTypes from 'prop-types';

import {getWorkflowName} from 'modules/utils/instance';

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
    selectedActivityInstanceId: PropTypes.string,
    onActivityInstanceSelected: PropTypes.func
  };

  static defaultProps = {
    onActivityInstanceSelected: () => {}
  };

  renderLogEntry = ([id, {state, type, name}]) => {
    const {selectedActivityInstanceId} = this.props;

    return (
      <Styled.LogEntry key={id} isSelected={id === selectedActivityInstanceId}>
        <Styled.LogEntryToggle
          data-test={id}
          isSelected={id === selectedActivityInstanceId}
          onClick={() => this.props.onActivityInstanceSelected(id)}
        >
          <Styled.FlowNodeIcon
            state={state}
            type={type}
            isSelected={id === selectedActivityInstanceId}
          />
          {name}
        </Styled.LogEntryToggle>
      </Styled.LogEntry>
    );
  };

  render() {
    const {instance, activitiesDetails} = this.props;

    return (
      <Styled.InstanceLog {...this.props}>
        <Styled.EntriesContainer>
          <Styled.LogEntry
            isSelected={this.props.selectedActivityInstanceId === null}
          >
            >
            <Styled.HeaderToggle
              isSelected={this.props.selectedActivityInstanceId === null}
              onClick={() => this.props.onActivityInstanceSelected(null)}
            >
              <Styled.DocumentIcon
                isSelected={this.props.selectedActivityInstanceId === null}
              />
              {getWorkflowName(instance)}
            </Styled.HeaderToggle>
          </Styled.LogEntry>
          {activitiesDetails
            ? Object.entries(activitiesDetails).map(this.renderLogEntry)
            : null}
        </Styled.EntriesContainer>
      </Styled.InstanceLog>
    );
  }
}
