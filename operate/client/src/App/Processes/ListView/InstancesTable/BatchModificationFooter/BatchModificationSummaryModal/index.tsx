/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {observer} from 'mobx-react';
import {Modal} from '@carbon/react';
import {useLocation} from 'react-router-dom';
import {type StateProps} from 'modules/components/ModalStateManager';
import {getProcessInstanceFilters} from 'modules/utils/filter';
import {batchModificationStore} from 'modules/stores/batchModification';
import {Title, DataTable} from './styled';
import {tracking} from 'modules/tracking';
import {useInstancesCount} from 'modules/queries/processInstancesStatistics/useInstancesCount';
import {useListViewXml} from 'modules/queries/processDefinitions/useListViewXml';
import {getFlowNodeName} from 'modules/utils/flowNodes';
import {handleOperationError} from 'modules/utils/notifications';
import {useModifyProcessInstancesBatchOperation} from 'modules/mutations/processes/useModifyProcessInstancesBatchOperation';
import {useBatchOperationMutationRequestBody} from 'modules/hooks/useBatchOperationMutationRequestBody';
import {useBatchOperationSuccessNotification} from 'modules/hooks/useBatchOperationSuccessNotification';
import {useSelectedProcessDefinitionContext} from '../../../selectedProcessDefinitionContext';
import {getProcessDefinitionName} from 'modules/hooks/processDefinitions';

const BatchModificationSummaryModal: React.FC<StateProps> = observer(
  ({open, setOpen}) => {
    const displaySuccessNotification = useBatchOperationSuccessNotification();
    const location = useLocation();
    const batchOperationMutationRequestBody =
      useBatchOperationMutationRequestBody();

    const processInstancesFilters = getProcessInstanceFilters(location.search);
    const {flowNodeId: sourceElementId} = processInstancesFilters;
    const process = useSelectedProcessDefinitionContext();
    const processName = process ? getProcessDefinitionName(process) : 'Process';
    const {selectedTargetElementId} = batchModificationStore.state;

    const {data: processDefinitionData} = useListViewXml({
      processDefinitionKey: process?.processDefinitionKey,
    });

    const sourceFlowNodeName = getFlowNodeName({
      businessObjects: processDefinitionData?.diagramModel.elementsById,
      flowNodeId: sourceElementId,
    });

    const targetFlowNodeName = getFlowNodeName({
      businessObjects: processDefinitionData?.diagramModel.elementsById,
      flowNodeId: selectedTargetElementId ?? undefined,
    });

    const {data: instancesCount} = useInstancesCount(
      {},
      process?.processDefinitionKey,
      sourceElementId,
    );

    const isPrimaryButtonDisabled =
      !sourceElementId || selectedTargetElementId === null;

    const {mutate: batchModifyProcessInstances} =
      useModifyProcessInstancesBatchOperation({
        onSuccess: ({batchOperationKey, batchOperationType}) => {
          displaySuccessNotification(batchOperationType, batchOperationKey);
          batchModificationStore.reset();
          tracking.track({
            eventName: 'batch-operation',
            operationType: 'MODIFY_PROCESS_INSTANCE',
          });
        },
        onError: (error) => {
          handleOperationError(error.response?.status);
        },
      });

    const headers = [
      {header: 'Operation', key: 'operation', width: '30%'},
      {header: 'Element', key: 'flowNode', width: '40%'},
      {header: 'Affected instances', key: 'affectedInstances', width: '30%'},
    ];

    const rows = [
      {
        id: 'batchMove',
        operation: 'Batch move',
        flowNode: `${sourceFlowNodeName} --> ${targetFlowNodeName}`,
        affectedInstances: instancesCount,
      },
    ];

    return (
      <Modal
        primaryButtonDisabled={isPrimaryButtonDisabled}
        modalHeading="Apply Modifications"
        size="lg"
        primaryButtonText="Apply"
        secondaryButtonText="Cancel"
        open={open}
        onRequestClose={() => setOpen(false)}
        preventCloseOnClickOutside
        onRequestSubmit={() => {
          if (!sourceElementId || !selectedTargetElementId) {
            return;
          }

          tracking.track({
            eventName: 'batch-move-modification-apply-button-clicked',
          });
          setOpen(false);
          batchModificationStore.reset();

          batchModifyProcessInstances({
            ...batchOperationMutationRequestBody,
            moveInstructions: [
              {
                sourceElementId,
                targetElementId: selectedTargetElementId,
              },
            ],
          });

          setOpen(false);
        }}
      >
        <p>
          {`Planned modifications for "${processName}". Click "Apply" to proceed.`}
        </p>
        <Title>Element Modifications</Title>
        <DataTable headers={headers} rows={rows} />
      </Modal>
    );
  },
);

export {BatchModificationSummaryModal};
