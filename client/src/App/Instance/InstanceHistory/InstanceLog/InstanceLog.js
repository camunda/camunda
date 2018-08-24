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
    selectedActivity: PropTypes.string,
    onActivitySelected: PropTypes.func
  };

  static defaultProps = {
    onActivitySelected: () => {}
  };

  state = {
    selected: HEADER
  };

  renderLogEntry = ([id, {state, type, name}]) => {
    const {activitiesDetails, selectedActivity} = this.props;
    const selectedActivityInstance = (Object.entries(activitiesDetails).filter(
      ([_, {activityId}]) => {
        return activityId === selectedActivity;
      }
    )[0] || [])[0];

    return (
      <Styled.LogEntry key={id}>
        <Styled.LogEntryToggle
          data-test-key={id}
          isSelected={id === selectedActivityInstance}
          onClick={() =>
            this.props.onActivitySelected(activitiesDetails[id].activityId)
          }
        >
          <Styled.FlowNodeIcon
            state={state}
            type={type}
            isSelected={id === selectedActivityInstance}
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
        <Styled.LogEntry>
          <Styled.HeaderToggle
            isSelected={this.props.selectedActivity === null}
            onClick={() => this.props.onActivitySelected(null)}
          >
            <Styled.DocumentIcon
              isSelected={this.props.selectedActivity === null}
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
