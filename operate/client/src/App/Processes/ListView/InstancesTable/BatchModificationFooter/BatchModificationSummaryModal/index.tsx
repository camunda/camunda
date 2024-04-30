/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
import {processStatisticsBatchModificationStore} from 'modules/stores/processStatistics/processStatistics.batchModification';
import {Title, DataTable} from './styled';
import useOperationApply from '../../useOperationApply';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';

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
    const instancesCount =
      processStatisticsBatchModificationStore.getInstancesCount(
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
