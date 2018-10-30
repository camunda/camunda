import React from 'react';
import PropTypes from 'prop-types';

import {OPERATION_TYPE, OPERATION_STATE} from 'modules/constants';
import {applyOperation} from 'modules/api/instances';

import ActionItems from './ActionItems';
import StatusItems from './StatusItems';

import {
  wrapIdinQuery,
  isWithIncident,
  isRunning,
  getLatestOperation,
  getLatestOperationState
} from './service';

import * as Styled from './styled';

export default class Actions extends React.Component {
  static propTypes = {
    instance: PropTypes.object.isRequired,
    selected: PropTypes.bool
  };

  state = {operationState: '', operationType: ''};

  componentDidMount = () => {
    let operationType;
    const operationState = getLatestOperationState(
      this.props.instance.operations
    );

    if (this.props.instance.operations.length > 0) {
      operationType = getLatestOperation(this.props.instance.operations).type;
    }

    this.setState({operationState, operationType});
  };

  componentDidUpdate = prevProps => {
    const {operations} = this.props.instance;
    let operationState;
    let operationType;

    if (operations.length > prevProps.instance.operations.length) {
      // change operation state & failed operation icons when new page is loaded;
      operationState = getLatestOperationState(operations);
      operationType = getLatestOperation(operations).type;

      this.setState({operationState, operationType});
    } else if (this.props.instance.id !== prevProps.instance.id) {
      // change operation state
      operationState = getLatestOperationState(operations);

      this.setState({operationState});
    }
  };

  handleOnClick = operationType => {
    this.setState({operationState: OPERATION_STATE.SCHEDULED});
    applyOperation(
      operationType,
      wrapIdinQuery(operationType, this.props.instance)
    );
  };

  renderItem = operationType => (
    <ActionItems.Item
      type={operationType}
      onClick={() => this.handleOnClick(operationType)}
    />
  );

  renderFailedStatus = () =>
    this.state.operationState === OPERATION_STATE.FAILED && (
      <StatusItems>
        <StatusItems.Item type={this.state.operationType} />
      </StatusItems>
    );

  render() {
    return (
      <Styled.Actions>
        {this.state.operationState === OPERATION_STATE.SCHEDULED ? (
          <Styled.ActionSpinner selected={this.props.selected} />
        ) : (
          this.renderFailedStatus()
        )}
        <ActionItems>
          {isWithIncident(this.props.instance) &&
            this.renderItem(OPERATION_TYPE.UPDATE_RETRIES)}
          {isRunning(this.props.instance) &&
            this.renderItem(OPERATION_TYPE.CANCEL)}
        </ActionItems>
      </Styled.Actions>
    );
  }
}
