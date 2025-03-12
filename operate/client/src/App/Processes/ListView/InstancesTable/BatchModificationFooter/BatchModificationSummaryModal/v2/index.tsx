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
import {StateProps} from 'modules/components/ModalStateManager';
import {getProcessInstanceFilters} from 'modules/utils/filter';
import {processesStore} from 'modules/stores/processes/processes.list';
import {processXmlStore} from 'modules/stores/processXml/processXml.list';
import {batchModificationStore} from 'modules/stores/batchModification';
import {Title, DataTable} from '../styled';
import useOperationApply from '../../../useOperationApply';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import {useInstancesCount} from 'modules/queries/processInstancesStatistics/useInstancesCount';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';

const BatchModificationSummaryModal: React.FC<StateProps> = observer(
  ({open, setOpen}) => {
    const location = useLocation();

    const {applyBatchOperation} = useOperationApply();
    const processInstancesFilters = getProcessInstanceFilters(location.search);
    const {flowNodeId: sourceFlowNodeId, process: bpmnProcessId} =
      processInstancesFilters;
    const process = processesStore.getProcess({bpmnProcessId});
    const processName = process?.name ?? process?.bpmnProcessId ?? 'Process';
    const {selectedTargetFlowNodeId: targetFlowNodeId} =
      batchModificationStore.state;
    const sourceFlowNodeName = sourceFlowNodeId
      ? processXmlStore.getFlowNodeName(sourceFlowNodeId)
      : undefined;
    const targetFlowNodeName = targetFlowNodeId
      ? processXmlStore.getFlowNodeName(targetFlowNodeId)
      : undefined;
    const selectedProcessInstanceIds =
      processInstancesSelectionStore.selectedProcessInstanceIds;
    const {data: instancesCount} = useInstancesCount(
      {
        processInstanceKey: {
          $in: selectedProcessInstanceIds,
        },
      },
      sourceFlowNodeId,
    );
    const isPrimaryButtonDisabled =
      sourceFlowNodeId === undefined || targetFlowNodeId === null;
    const headers = [
      {
        header: 'Operation',
        key: 'operation',
        width: '30%',
      },
      {
        header: 'Flow Node',
        key: 'flowNode',
        width: '40%',
      },
      {
        header: 'Affected instances',
        key: 'affectedInstances',
        width: '30%',
      },
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
          if (isPrimaryButtonDisabled) {
            return;
          }
          tracking.track({
            eventName: 'batch-move-modification-apply-button-clicked',
          });
          setOpen(false);
          batchModificationStore.reset();
          applyBatchOperation({
            operationType: 'MODIFY_PROCESS_INSTANCE',
            modifications: [
              {
                modification: 'MOVE_TOKEN',
                fromFlowNodeId: sourceFlowNodeId,
                toFlowNodeId: targetFlowNodeId,
              },
            ],
            onSuccess: panelStatesStore.expandOperationsPanel,
          });
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
