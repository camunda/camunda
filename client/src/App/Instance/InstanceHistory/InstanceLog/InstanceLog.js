import React from 'react';
import PropTypes from 'prop-types';

import {getWorkflowName} from 'modules/utils/instance';
import {HEADER} from 'modules/constants';

import * as Styled from './styled';

export default class InstanceLog extends React.Component {
  static propTypes = {
    instanceLog: PropTypes.object
  };

  state = {
    selected: HEADER
  };

  setSelected = selected => {
    this.setState({selected});
  };

  renderLogEntry = ({state, type, name, id}) => (
    <Styled.LogEntry
      key={id}
      isSelected={this.state.selected === id}
      onClick={() => this.setSelected(id)}
    >
      <Styled.FlowNodeIcon
        state={state}
        type={type}
        isSelected={this.state.selected === id}
      />
      {name}
    </Styled.LogEntry>
  );

  render() {
    const {instanceLog} = this.props;

    return (
      <Styled.InstanceLog {...this.props}>
        {!instanceLog ? null : (
          <React.Fragment>
            <Styled.Header
              isSelected={this.state.selected === HEADER}
              onClick={() => this.setSelected(HEADER)}
            >
              <Styled.DocumentIcon
                isSelected={this.state.selected === HEADER}
              />
              {getWorkflowName(instanceLog)}
            </Styled.Header>
            {instanceLog.activities.map(this.renderLogEntry)}
          </React.Fragment>
        )}
      </Styled.InstanceLog>
    );
  }
}
