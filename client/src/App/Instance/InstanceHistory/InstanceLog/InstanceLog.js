import React from 'react';
import PropTypes from 'prop-types';

import {getWorkflowName} from 'modules/utils/instance';
import {HEADER} from 'modules/constants';

import {mapActivityIdToActivityInstanceId} from '../service';
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
    selectedActivityId: PropTypes.string,
    onActivitySelected: PropTypes.func
  };

  static defaultProps = {
    onActivitySelected: () => {}
  };

  state = {
    selected: HEADER
  };

  renderLogEntry = ([id, {state, type, name}]) => {
    const {activitiesDetails, selectedActivityId} = this.props;
    const selectedActivityInstanceId = mapActivityIdToActivityInstanceId(
      activitiesDetails,
      selectedActivityId
    );

    return (
      <Styled.LogEntry key={id}>
        <Styled.LogEntryToggle
          data-test={id}
          isSelected={id === selectedActivityInstanceId}
          onClick={() =>
            this.props.onActivitySelected(activitiesDetails[id].activityId)
          }
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
        <Styled.LogEntry>
          <Styled.HeaderToggle
            isSelected={this.props.selectedActivityId === null}
            onClick={() => this.props.onActivitySelected(null)}
          >
            <Styled.DocumentIcon
              isSelected={this.props.selectedActivityId === null}
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
