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
import {Title, DataTable} from './styled';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import {useInstancesCount} from 'modules/queries/processInstancesStatistics/useInstancesCount';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {useListViewXml} from 'modules/queries/processDefinitions/useListViewXml';
import {getFlowNodeName} from 'modules/utils/flowNodes';
import {notificationsStore} from 'modules/stores/notifications';
import {useModifyProcessInstancesBatchOperation} from 'modules/mutations/processes/useModifyProcessInstancesBatchOperation';
import {processInstancesStore} from 'modules/stores/processInstances';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {buildProcessInstanceKeyCriterion} from 'modules/mutations/processes/buildProcessInstanceKeyCriterion';

const BatchModificationSummaryModal: React.FC<StateProps> = observer(
  ({open, setOpen}) => {
    const location = useLocation();

    const processInstancesFilters = getProcessInstanceFilters(location.search);
    const {flowNodeId: sourceElementId, process: bpmnProcessId} =
      processInstancesFilters;
    const process = processesStore.getProcess({bpmnProcessId});
    const processName = process?.name ?? process?.bpmnProcessId ?? 'Process';
    const {selectedTargetElementId} = batchModificationStore.state;
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
      flowNodeId: sourceElementId,
    });

    const targetFlowNodeName = getFlowNodeName({
      businessObjects: processDefinitionData?.diagramModel.elementsById,
      flowNodeId: selectedTargetElementId ?? undefined,
    });

    const {data: instancesCount} = useInstancesCount(
      {},
      processDefinitionKey,
      sourceElementId,
    );

    const isPrimaryButtonDisabled =
      !sourceElementId || selectedTargetElementId === null;

    const {mutate: batchModifyProcessInstances} =
      useModifyProcessInstancesBatchOperation({
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
          if (!sourceElementId || !selectedTargetElementId) {
            return;
          }

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

          const keyCriterion = buildProcessInstanceKeyCriterion(
            ids,
            excludedProcessInstanceIds,
          );

          batchModifyProcessInstances({
            moveInstructions: [
              {
                sourceElementId,
                targetElementId: selectedTargetElementId,
              },
            ],
            filter: {
              processDefinitionKey,
              processInstanceKey: keyCriterion,
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
