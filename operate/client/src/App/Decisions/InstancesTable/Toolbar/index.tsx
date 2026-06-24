/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  TableToolbar,
  Modal,
  TableBatchAction,
  TableBatchActions,
} from '@carbon/react';
import {TrashCan} from '@carbon/react/icons';
import {observer} from 'mobx-react';
import {useState} from 'react';
import {decisionInstancesSelectionStore} from 'modules/stores/instancesSelection';
import {useDeleteDecisionInstancesBatchOperation} from 'modules/mutations/decisionInstances/useDeleteDecisionInstancesBatchOperation';
import {useDeleteDecisionInstancesBatchOperationRequestBody} from 'modules/hooks/useBatchOperationMutationRequestBody';
import {useBatchOperationSuccessNotification} from 'modules/hooks/useBatchOperationSuccessNotification';
import {handleOperationError} from 'modules/utils/notifications';
import {tracking} from 'modules/tracking';
import {pluralSuffix} from 'modules/utils/pluralSuffix';

type Props = {
  selectedCount: number;
};

const Toolbar: React.FC<Props> = observer(({selectedCount}) => {
  const displaySuccessNotification = useBatchOperationSuccessNotification();
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const deleteRequestBody =
    useDeleteDecisionInstancesBatchOperationRequestBody();

  const deleteMutation = useDeleteDecisionInstancesBatchOperation({
    onSuccess: ({batchOperationKey, batchOperationType}) => {
      displaySuccessNotification(batchOperationType, batchOperationKey);
      tracking.track({
        eventName: 'batch-operation',
        operationType: 'DELETE_DECISION_INSTANCE',
      });
      decisionInstancesSelectionStore.resetState();
    },
    onError: (error) => {
      handleOperationError(error.response?.status);
    },
  });

  if (selectedCount === 0) {
    return null;
  }

  return (
    <>
      <TableToolbar size="sm">
        <TableBatchActions
          shouldShowBatchActions
          totalSelected={selectedCount}
          onCancel={decisionInstancesSelectionStore.resetState}
          translateWithId={(id) => {
            switch (id) {
              case 'carbon.table.batch.cancel':
                return 'Discard';
              case 'carbon.table.batch.items.selected':
                return `${selectedCount} items selected`;
              case 'carbon.table.batch.item.selected':
                return `${selectedCount} item selected`;
              case 'carbon.table.batch.selectAll':
                return 'Select all items';
              default:
                return id;
            }
          }}
        >
          <TableBatchAction
            renderIcon={TrashCan}
            onClick={() => setShowDeleteModal(true)}
          >
            Delete
          </TableBatchAction>
        </TableBatchActions>
      </TableToolbar>

      <Modal
        open={showDeleteModal}
        preventCloseOnClickOutside
        modalHeading="Apply operation"
        primaryButtonText="Delete"
        danger
        secondaryButtonText="Cancel"
        onRequestSubmit={() => {
          deleteMutation.mutate(deleteRequestBody);
          setShowDeleteModal(false);
        }}
        onRequestClose={() => setShowDeleteModal(false)}
        onSecondarySubmit={() => {
          setShowDeleteModal(false);
          decisionInstancesSelectionStore.resetState();
        }}
        size="md"
      >
        <p>
          {`${pluralSuffix(selectedCount, 'instance')} selected for delete operation. This permanently deletes the selected decision instances and their history. This cannot be undone.`}
        </p>
      </Modal>
    </>
  );
});

export {Toolbar};
