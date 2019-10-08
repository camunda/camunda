/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {isEqual} from 'lodash';

import {OPERATION_TYPE, OPERATION_STATE} from 'modules/constants';
import {applyOperation} from 'modules/api/instances';
import {
  getLatestOperation,
  isWithIncident,
  isRunning
} from 'modules/utils/instance';

import ActionStatus from 'modules/components/ActionStatus';
import ActionItems from './ActionItems';

import * as Styled from './styled';

export default class Actions extends React.Component {
  static propTypes = {
    instance: PropTypes.object.isRequired,
    selected: PropTypes.bool,
    onButtonClick: PropTypes.func,
    forceSpinner: PropTypes.bool
  };

  static defaultProps = {
    forceSpinner: false
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
    if (!isEqual(operations, prevProps.instance.operations)) {
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

  handleOnClick = async operationType => {
    this.setState({operationState: OPERATION_STATE.SCHEDULED});
    await applyOperation(this.props.instance.id, {operationType});
    this.props.onButtonClick && this.props.onButtonClick(this.props.instance);
  };

  renderItem = operationType => {
    const ariaLabelMap = {
      [OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE]: 'Cancel',
      [OPERATION_TYPE.RESOLVE_INCIDENT]: 'Retry'
    };

    return (
      <ActionItems.Item
        type={operationType}
        onClick={() => this.handleOnClick(operationType)}
        title={`${ariaLabelMap[operationType]} Instance ${this.props.instance.id}`}
      />
    );
  };

  renderActionButtons = () => (
    <ActionItems>
      {isWithIncident(this.props.instance) &&
        this.renderItem(OPERATION_TYPE.RESOLVE_INCIDENT)}
      {isRunning(this.props.instance) &&
        this.renderItem(OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE)}
    </ActionItems>
  );

  render() {
    return (
      <Styled.Actions>
        <ActionStatus
          forceSpinner={this.props.forceSpinner}
          operationState={this.state.operationState}
          operationType={this.state.operationType}
          selected={this.props.selected}
          instance={this.props.instance}
        />
        {this.renderActionButtons()}
      </Styled.Actions>
    );
  }
}
