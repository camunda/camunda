/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {isEqual} from 'lodash';
import {OPERATION_TYPE, OPERATION_STATE} from 'modules/constants';
import {ACTIVE_OPERATION_STATES} from 'modules/constants';
import {operationsStore} from 'modules/stores/operations';
import {instances as instancesStore} from 'modules/stores/instances';
import {observer} from 'mobx-react';

import {
  getLatestOperation,
  isWithIncident,
  isRunning,
} from 'modules/utils/instance';

import OperationItems from 'modules/components/OperationItems';
import {OperationSpinner} from 'modules/components/OperationSpinner';

import * as Styled from './styled';

const Operations = observer(
  class Operations extends React.Component {
    static propTypes = {
      instance: PropTypes.object.isRequired,
      selected: PropTypes.bool,
      onButtonClick: PropTypes.func,
      forceSpinner: PropTypes.bool,
    };

    static defaultProps = {
      forceSpinner: false,
    };

    state = {operationState: '', operationType: ''};

    componentDidMount = () => {
      const {operations} = this.props.instance;
      if (operations.length > 0) {
        const {type, state} = getLatestOperation(operations);
        this.setState({
          operationState: state,
          operationType: type,
        });
      }
    };

    componentDidUpdate = (prevProps) => {
      const {operations} = this.props.instance;
      if (!isEqual(operations, prevProps.instance.operations)) {
        // change operation state & failed operation icons when new page is loaded;
        const {state, type} = getLatestOperation(operations);
        this.setState({
          operationState: state,
          operationType: type,
        });
      } else if (this.props.instance.id !== prevProps.instance.id) {
        // change operation state
        const {state} = getLatestOperation(operations);

        this.setState({operationState: state});
      }
    };

    handleOnClick = async (operationType) => {
      this.setState({operationState: OPERATION_STATE.SCHEDULED});

      operationsStore.applyOperation(this.props.instance.id, {
        operationType,
      });
      this.props.onButtonClick && this.props.onButtonClick(this.props.instance);
    };

    renderItem = (operationType) => {
      const ariaLabelMap = {
        [OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE]: 'Cancel',
        [OPERATION_TYPE.RESOLVE_INCIDENT]: 'Retry',
      };

      return (
        <OperationItems.Item
          type={operationType}
          onClick={() => this.handleOnClick(operationType)}
          title={`${ariaLabelMap[operationType]} Instance ${this.props.instance.id}`}
        />
      );
    };

    render() {
      const {instance, selected, forceSpinner} = this.props;
      return (
        <Styled.Operations>
          {(forceSpinner ||
            ACTIVE_OPERATION_STATES.includes(this.state.operationState) ||
            instancesStore.state.instancesWithActiveOperations.includes(
              instance.id
            )) && (
            <OperationSpinner
              selected={selected}
              title={`Instance ${instance.id} has scheduled Operations`}
              data-testid="operation-spinner"
            />
          )}
          <OperationItems>
            {isWithIncident(this.props.instance) &&
              this.renderItem(OPERATION_TYPE.RESOLVE_INCIDENT)}
            {isRunning(this.props.instance) &&
              this.renderItem(OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE)}
          </OperationItems>
        </Styled.Operations>
      );
    }
  }
);

export {Operations};
