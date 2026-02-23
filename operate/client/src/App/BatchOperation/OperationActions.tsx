/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {OverflowMenu, OverflowMenuItem, Button} from '@carbon/react';
import {Pause, Play} from '@carbon/react/icons';
import type {
  BatchOperationState,
  BatchOperationType,
} from '@camunda/camunda-api-zod-schemas/8.8/batch-operation';
import {useSuspendBatchOperation} from 'modules/mutations/batchOperations/useSuspendBatchOperation';
import {useResumeBatchOperation} from 'modules/mutations/batchOperations/useResumeBatchOperation';
import {useCancelBatchOperation} from 'modules/mutations/batchOperations/useCancelBatchOperation';
import {ActionsContainer} from './styled';
import {handleOperationError} from 'modules/utils/notifications';
import {tracking} from 'modules/tracking';

type Props = {
  batchOperationKey: string;
  batchOperationState: BatchOperationState;
  batchOperationType: BatchOperationType;
};

type OperationAction = 'SUSPEND' | 'RESUME' | 'CANCEL';

const ACTIONS_BY_STATE: Record<BatchOperationState, OperationAction[]> = {
  CREATED: ['SUSPEND', 'CANCEL'],
  ACTIVE: ['SUSPEND', 'CANCEL'],
  SUSPENDED: ['RESUME', 'CANCEL'],
  COMPLETED: [],
  PARTIALLY_COMPLETED: [],
  FAILED: [],
  CANCELED: [],
};

const OperationsActions: React.FC<Props> = ({
  batchOperationKey,
  batchOperationState,
  batchOperationType,
}) => {
  const suspendMutation = useSuspendBatchOperation({
    onSuccess: () => {
      tracking.track({
        eventName: 'batch-operation-suspended',
        batchOperationType,
        batchOperationState,
      });
    },
    onError: (error) => {
      handleOperationError(error.response?.status);
    },
  });
  const resumeMutation = useResumeBatchOperation({
    onSuccess: () => {
      tracking.track({
        eventName: 'batch-operation-resumed',
        batchOperationType,
        batchOperationState,
      });
    },
    onError: (error) => {
      handleOperationError(error.response?.status);
    },
  });
  const cancelMutation = useCancelBatchOperation({
    onSuccess: () => {
      tracking.track({
        eventName: 'batch-operation-canceled',
        batchOperationType,
        batchOperationState,
      });
    },
    onError: (error) => {
      handleOperationError(error.response?.status);
    },
  });

  const allowedActions = ACTIONS_BY_STATE[batchOperationState];

  return (
    <ActionsContainer gap={2} orientation="horizontal">
      {allowedActions.includes('SUSPEND') && (
        <Button
          kind="tertiary"
          renderIcon={Pause}
          onClick={() => {
            suspendMutation.mutate(batchOperationKey);
          }}
          disabled={suspendMutation.isPending}
        >
          Suspend
        </Button>
      )}
      {allowedActions.includes('RESUME') && (
        <Button
          kind="tertiary"
          renderIcon={Play}
          onClick={() => {
            resumeMutation.mutate(batchOperationKey);
          }}
          disabled={resumeMutation.isPending}
        >
          Resume
        </Button>
      )}
      {allowedActions.includes('CANCEL') && (
        <OverflowMenu aria-label="overflow-menu" flipped>
          <OverflowMenuItem
            itemText="Cancel"
            isDelete
            onClick={() => {
              cancelMutation.mutate(batchOperationKey);
            }}
            disabled={cancelMutation.isPending}
          />
        </OverflowMenu>
      )}
    </ActionsContainer>
  );
};

export {OperationsActions};
