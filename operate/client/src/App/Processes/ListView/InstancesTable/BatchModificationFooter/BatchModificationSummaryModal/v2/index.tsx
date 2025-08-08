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
import {
  getProcessInstanceFilters,
  getProcessInstancesRequestFilters,
} from 'modules/utils/filter';
import {processesStore} from 'modules/stores/processes/processes.list';
import {batchModificationStore} from 'modules/stores/batchModification';
import {Title, DataTable} from '../styled';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import {useInstancesCount} from 'modules/queries/processInstancesStatistics/useInstancesCount';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {useListViewXml} from 'modules/queries/processDefinitions/useListViewXml';
import {getFlowNodeName} from 'modules/utils/flowNodes';
import {notificationsStore} from 'modules/stores/notifications';
import {useModifyProcessInstanceBatchOperation} from 'modules/mutations/processInstance/useModifyProcessInstanceBatchOperation';
import {processInstancesStore} from 'modules/stores/processInstances';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';

const BatchModificationSummaryModal: React.FC<StateProps> = observer(
  ({open, setOpen}) => {
    const location = useLocation();

    const processInstancesFilters = getProcessInstanceFilters(location.search);
    const {flowNodeId: sourceFlowNodeId, process: bpmnProcessId} =
      processInstancesFilters;
    const process = processesStore.getProcess({bpmnProcessId});
    const processName = process?.name ?? process?.bpmnProcessId ?? 'Process';
    const {selectedTargetFlowNodeId: targetFlowNodeId} =
      batchModificationStore.state;
    const {selectedProcessInstanceIds, excludedProcessInstanceIds} =
      processInstancesSelectionStore;

    const query = getProcessInstancesRequestFilters();
    const filterIds = query.ids || [];

    const ids: string[] =
      selectedProcessInstanceIds.length > 0
        ? selectedProcessInstanceIds
        : filterIds;

    const processDefinitionKey = useProcessDefinitionKeyContext();
    const {data: processDefinitionData} = useListViewXml({
      processDefinitionKey,
    });

    const sourceFlowNodeName = getFlowNodeName({
      businessObjects: processDefinitionData?.diagramModel.elementsById,
      flowNodeId: sourceFlowNodeId,
    });

    const targetFlowNodeName = getFlowNodeName({
      businessObjects: processDefinitionData?.diagramModel.elementsById,
      flowNodeId: targetFlowNodeId ?? undefined,
    });

    const {data: instancesCount} = useInstancesCount(
      {},
      processDefinitionKey,
      sourceFlowNodeId,
    );

    const isPrimaryButtonDisabled =
      !sourceFlowNodeId || targetFlowNodeId === null;

    const mutation = useModifyProcessInstanceBatchOperation({
      onSuccess: () => {
        panelStatesStore.expandOperationsPanel();
        batchModificationStore.reset();
        tracking.track({
          eventName: 'batch-operation',
          operationType: 'MODIFY_PROCESS_INSTANCE',
        });
      },
      onError: ({message}) => {
        processInstancesStore.unmarkProcessInstancesWithActiveOperations({
          instanceIds: ids,
          operationType: 'MODIFY_PROCESS_INSTANCE',
          shouldPollAllVisibleIds: selectedProcessInstanceIds.length === 0,
        });
        notificationsStore.displayNotification({
          kind: 'error',
          title: 'Operation could not be created',
          subtitle: message.includes('403')
            ? 'You do not have permission'
            : undefined,
          isDismissable: true,
        });
      },
    });

    const headers = [
      {header: 'Operation', key: 'operation', width: '30%'},
      {header: 'Flow Node', key: 'flowNode', width: '40%'},
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
          if (!sourceFlowNodeId || !targetFlowNodeId) return;

          tracking.track({
            eventName: 'batch-move-modification-apply-button-clicked',
          });
          setOpen(false);
          batchModificationStore.reset();

          if (selectedProcessInstanceIds.length > 0) {
            processInstancesStore.markProcessInstancesWithActiveOperations({
              ids: selectedProcessInstanceIds,
              operationType: 'MODIFY_PROCESS_INSTANCE',
            });
          } else {
            processInstancesStore.markProcessInstancesWithActiveOperations({
              ids: excludedProcessInstanceIds,
              operationType: 'MODIFY_PROCESS_INSTANCE',
              shouldPollAllVisibleIds: true,
            });
          }

          const processInstanceKey: {$in?: string[]; $notIn?: string[]} = {};
          if (ids.length) processInstanceKey.$in = ids;
          if (excludedProcessInstanceIds.length)
            processInstanceKey.$notIn = excludedProcessInstanceIds;

          mutation.mutate({
            moveInstructions: [
              {
                sourceElementId: sourceFlowNodeId,
                targetElementId: targetFlowNodeId,
              },
            ],
            filter: {
              processDefinitionKey: {$eq: processDefinitionKey},
              ...(Object.keys(processInstanceKey).length > 0 && {
                processInstanceKey,
              }),
            },
          });

          setOpen(false);
        }}
      >
        <p>
          {`Planned modifications for "${processName}". Click "Apply" to proceed.`}
        </p>
        <Title>Flow Node Modifications</Title>
        <DataTable headers={headers} rows={rows} />
      </Modal>
    );
  },
);

export {BatchModificationSummaryModal};
