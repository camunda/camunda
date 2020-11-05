/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {isEqual} from 'lodash';
import {OPERATION_TYPE, OPERATION_STATE} from 'modules/constants';
import {ACTIVE_OPERATION_STATES} from 'modules/constants';
import {operationsStore} from 'modules/stores/operations';
import {instancesStore} from 'modules/stores/instances';
import {observer} from 'mobx-react';

import {
  getLatestOperation,
  isWithIncident,
  isRunning,
} from 'modules/utils/instance';

import OperationItems from 'modules/components/OperationItems';
import {OperationSpinner} from 'modules/components/OperationSpinner';

import * as Styled from './styled';

type Props = {
  instance: unknown;
  selected?: boolean;
  onButtonClick?: () => void;
  forceSpinner?: boolean;
};

const Operations = observer(
  class Operations extends React.Component<Props, {}> {
    static defaultProps = {
      forceSpinner: false,
    };

    state = {operationState: '', operationType: ''};

    componentDidMount = () => {
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'instance' does not exist on type 'Readon... Remove this comment to see the full error message
      const {operations} = this.props.instance;
      if (operations.length > 0) {
        // @ts-expect-error ts-migrate(2339) FIXME: Property 'type' does not exist on type '{}'.
        const {type, state} = getLatestOperation(operations);
        this.setState({
          operationState: state,
          operationType: type,
        });
      }
    };

    componentDidUpdate = (prevProps: any) => {
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'instance' does not exist on type 'Readon... Remove this comment to see the full error message
      const {operations} = this.props.instance;
      if (!isEqual(operations, prevProps.instance.operations)) {
        // change operation state & failed operation icons when new page is loaded;
        // @ts-expect-error ts-migrate(2339) FIXME: Property 'state' does not exist on type '{}'.
        const {state, type} = getLatestOperation(operations);
        this.setState({
          operationState: state,
          operationType: type,
        });
        // @ts-expect-error ts-migrate(2339) FIXME: Property 'instance' does not exist on type 'Readon... Remove this comment to see the full error message
      } else if (this.props.instance.id !== prevProps.instance.id) {
        // change operation state
        // @ts-expect-error ts-migrate(2339) FIXME: Property 'state' does not exist on type '{}'.
        const {state} = getLatestOperation(operations);

        this.setState({operationState: state});
      }
    };

    handleOnClick = async (operationType: any) => {
      this.setState({operationState: OPERATION_STATE.SCHEDULED});

      // @ts-expect-error ts-migrate(2339) FIXME: Property 'instance' does not exist on type 'Readon... Remove this comment to see the full error message
      operationsStore.applyOperation(this.props.instance.id, {
        operationType,
      });
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'onButtonClick' does not exist on type 'R... Remove this comment to see the full error message
      this.props.onButtonClick && this.props.onButtonClick(this.props.instance);
    };

    renderItem = (operationType: any) => {
      const ariaLabelMap = {
        [OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE]: 'Cancel',
        [OPERATION_TYPE.RESOLVE_INCIDENT]: 'Retry',
      };

      return (
        <OperationItems.Item
          type={operationType}
          onClick={() => this.handleOnClick(operationType)}
          // @ts-expect-error ts-migrate(2339) FIXME: Property 'instance' does not exist on type 'Readon... Remove this comment to see the full error message
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
              // @ts-expect-error ts-migrate(2345) FIXME: Argument of type 'any' is not assignable to parame... Remove this comment to see the full error message
              instance.id
            )) && (
            <OperationSpinner
              selected={selected}
              // @ts-expect-error
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
