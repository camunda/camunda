/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {isEqual} from 'lodash';
import {OPERATION_TYPE} from 'modules/constants';
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
  instance: ProcessInstanceEntity;
  selected?: boolean;
  onOperation?: () => void;
  onFailure?: () => void;
  forceSpinner?: boolean;
};

type State = {
  operationType: InstanceOperationEntity['type'] | undefined;
};

const Operations = observer(
  class Operations extends React.Component<Props, State> {
    static defaultProps = {
      forceSpinner: false,
    };

    state = {operationType: undefined};

    componentDidMount = () => {
      const {operations} = this.props.instance;
      if (operations.length > 0) {
        const operation = getLatestOperation(operations);
        this.setState({
          operationType: operation?.type,
        });
      }
    };

    componentDidUpdate = (prevProps: any) => {
      const {operations} = this.props.instance;
      if (!isEqual(operations, prevProps.instance.operations)) {
        // change failed operation icons when new page is loaded;
        const operation = getLatestOperation(operations);
        this.setState({
          operationType: operation?.type,
        });
      }
    };

    handleOnClick = async (operationType: InstanceOperationEntity['type']) => {
      operationsStore.applyOperation({
        instanceId: this.props.instance.id,
        payload: {
          operationType,
        },
        onError: () => {
          this.props.onFailure?.();
        },
      });
      this.props.onOperation?.();
    };

    renderItem = (operationType: InstanceOperationEntity['type']) => {
      const ariaLabelMap = {
        [OPERATION_TYPE.CANCEL_PROCESS_INSTANCE]: 'Cancel',
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
            instancesStore.instanceIdsWithActiveOperations.includes(
              instance.id
            )) && (
            <OperationSpinner
              selected={selected}
              title={`Instance ${instance.id} has scheduled Operations`}
              data-testid="operation-spinner"
            />
          )}
          <OperationItems>
            {isWithIncident(instance) &&
              this.renderItem(OPERATION_TYPE.RESOLVE_INCIDENT)}
            {isRunning(instance) &&
              this.renderItem(OPERATION_TYPE.CANCEL_PROCESS_INSTANCE)}
          </OperationItems>
        </Styled.Operations>
      );
    }
  }
);

export {Operations};
