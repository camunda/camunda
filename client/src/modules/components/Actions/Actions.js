import React from 'react';
import PropTypes from 'prop-types';

import {OPERATION_TYPE, OPERATION_STATE} from 'modules/constants';
import {applyOperation} from 'modules/api/instances';

import ActionSpinner from './ActionSpinner';
import ActionItems from './ActionItems';
import StatusItems from './StatusItems';

import {
  wrapIdinQuery,
  isWithIncident,
  isRunning,
  getLatestOperation
} from './service';

import * as Styled from './styled';

export default class Actions extends React.Component {
  static propTypes = {
    instance: PropTypes.object.isRequired,
    selected: PropTypes.bool
  };

  state = {operationState: '', operationType: ''};

  componentDidMount = () => {
    const {operations} = this.props.instance;

    if (operations.length > 0) {
      const {type, state} = getLatestOperation(operations);

      this.setState({
        operationState: state,
        operationType: type
      });
    }
  };

  componentDidUpdate = prevProps => {
    const {operations} = this.props.instance;

    if (operations.length > prevProps.instance.operations.length) {
      // change operation state & failed operation icons when new page is loaded;
      const {state, type} = getLatestOperation(operations);

      this.setState({
        operationState: state,
        operationType: type
      });
    } else if (this.props.instance.id !== prevProps.instance.id) {
      // change operation state
      const {state} = getLatestOperation(operations);

      this.setState({operationState: state});
    }
  };

  handleOnClick = operationType => {
    this.setState({operationState: OPERATION_STATE.SCHEDULED});
    applyOperation(
      operationType,
      wrapIdinQuery(operationType, this.props.instance)
    );
  };

  renderFailedStatus = () =>
    this.state.operationState === OPERATION_STATE.FAILED && (
      <StatusItems>
        <StatusItems.Item type={this.state.operationType} />
      </StatusItems>
    );

  renderItem = operationType => (
    <ActionItems.Item
      type={operationType}
      onClick={() => this.handleOnClick(operationType)}
    />
  );

  renderActionButtons = () => (
    <ActionItems>
      {isWithIncident(this.props.instance) &&
        this.renderItem(OPERATION_TYPE.UPDATE_RETRIES)}
      {isRunning(this.props.instance) && this.renderItem(OPERATION_TYPE.CANCEL)}
    </ActionItems>
  );

  render() {
    return (
      <Styled.Actions>
        <ActionSpinner
          operationState={this.state.operationState}
          selected={this.props.selected}
        />
        {this.renderFailedStatus()}
        {this.renderActionButtons()}
      </Styled.Actions>
    );
  }
}
