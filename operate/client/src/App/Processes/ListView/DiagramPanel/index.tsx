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

import {useOperationsPanelResize} from 'modules/hooks/useOperationsPanelResize';
import {useEffect, useRef} from 'react';
import {useLocation, useNavigate, Location} from 'react-router-dom';
import {deleteSearchParams} from 'modules/utils/filter';
import {getProcessInstanceFilters} from 'modules/utils/filter/getProcessInstanceFilters';

import {COLLAPSABLE_PANEL_MIN_WIDTH} from 'modules/constants';
import {Restricted} from 'modules/components/Restricted';
import {processesStore} from 'modules/stores/processes/processes.list';
import {ProcessOperations} from '../ProcessOperations';
import {PanelHeader, Section} from './styled';
import {DiagramShell} from 'modules/components/DiagramShell';
import {Diagram} from 'modules/components/Diagram';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {observer} from 'mobx-react';
import {StateOverlay} from 'modules/components/StateOverlay';
import {processXmlStore} from 'modules/stores/processXml/processXml.list';
import {processStatisticsStore} from 'modules/stores/processStatistics/processStatistics.list';
import {CopiableProcessID} from 'App/Processes/CopiableProcessID';
import {batchModificationStore} from 'modules/stores/batchModification';
import {isMoveModificationTarget} from 'modules/bpmn-js/utils/isMoveModificationTarget';
import {ModificationBadgeOverlay} from 'App/ProcessInstance/TopPanel/ModificationBadgeOverlay';
import {processStatisticsBatchModificationStore} from 'modules/stores/processStatistics/processStatistics.batchModification';
import {BatchModificationNotification} from './BatchModificationNotification';

const OVERLAY_TYPE_BATCH_MODIFICATIONS_BADGE = 'batchModificationsBadge';

type ModificationBadgePayload = {
  newTokenCount: number;
  cancelledTokenCount: number;
};

function setSearchParam(
  location: Location,
  [key, value]: [key: string, value: string],
) {
  const params = new URLSearchParams(location.search);

  params.set(key, value);

  return {
    ...location,
    search: params.toString(),
  };
}

const DiagramPanel: React.FC = observer(() => {
  const navigate = useNavigate();
  const location = useLocation();
  const {process, version, flowNodeId, tenant} = getProcessInstanceFilters(
    location.search,
  );

  const isVersionSelected = version !== undefined && version !== 'all';

  const {bpmnProcessId, processName} =
    processesStore.getSelectedProcessDetails();

  const isDiagramLoading =
    processXmlStore.state.status === 'fetching' ||
    !processesStore.isInitialLoadComplete ||
    (processesStore.state.status === 'fetching' &&
      location.state?.refreshContent);

  const statisticsOverlays = diagramOverlaysStore.state.overlays.filter(
    ({type}) => type.match(/^statistics/) !== null,
  );

  const batchModificationBadgeOverlays =
    diagramOverlaysStore.state.overlays.filter(
      ({type}) => type === OVERLAY_TYPE_BATCH_MODIFICATIONS_BADGE,
    );

  const processId = processesStore.getProcessId({process, tenant, version});

  const {selectedTargetFlowNodeId} = batchModificationStore.state;

  useEffect(() => {
    processStatisticsStore.init();
    return () => {
      processXmlStore.reset();
      processStatisticsStore.reset();
    };
  }, []);

  useEffect(() => {
    if (processId === undefined) {
      processXmlStore.reset();
      processStatisticsStore.reset();
      return;
    }

    const fetchDiagram = async () => {
      await processXmlStore.fetchProcessXml(processId);
      await processStatisticsStore.fetchProcessStatistics();
    };

    fetchDiagram();
  }, [processId, location.search]);

  const {xml} = processXmlStore.state;

  const panelHeaderRef = useRef<HTMLDivElement>(null);

  useOperationsPanelResize(panelHeaderRef, (target, width) => {
    target.style['marginRight'] =
      `calc(${width}px - ${COLLAPSABLE_PANEL_MIN_WIDTH})`;
  });

  const getStatus = () => {
    if (isDiagramLoading) {
      return 'loading';
    }
    if (processXmlStore.state.status === 'error') {
      return 'error';
    }
    if (!isVersionSelected) {
      return 'empty';
    }
    return 'content';
  };

  return (
    <Section>
      <PanelHeader title={processName} ref={panelHeaderRef}>
        <>
          <CopiableProcessID bpmnProcessId={bpmnProcessId} />
          {isVersionSelected && processId !== undefined && (
            <Restricted
              scopes={['write']}
              resourceBasedRestrictions={{
                scopes: ['DELETE'],
                permissions: processesStore.getPermissions(
                  bpmnProcessId,
                  tenant,
                ),
              }}
            >
              <ProcessOperations
                processDefinitionId={processId}
                processName={processName}
                processVersion={version}
              />
            </Restricted>
          )}
        </>
      </PanelHeader>
      <DiagramShell
        status={getStatus()}
        emptyMessage={
          version === 'all'
            ? {
                message: `There is more than one Version selected for Process "${processName}"`,
                additionalInfo: 'To see a Diagram, select a single Version',
              }
            : {
                message: 'There is no Process selected',
                additionalInfo:
                  'To see a Diagram, select a Process in the Filters panel',
              }
        }
      >
        {xml !== null && (
          <Diagram
            xml={xml}
            {...(batchModificationStore.state.isEnabled
              ? // Props for batch modification mode
                {
                  // Source and target flow node
                  selectedFlowNodeIds: [
                    ...(flowNodeId ? [flowNodeId] : []),
                    ...(selectedTargetFlowNodeId
                      ? [selectedTargetFlowNodeId]
                      : []),
                  ],
                  onFlowNodeSelection: (flowNodeId) => {
                    return batchModificationStore.selectTargetFlowNode(
                      flowNodeId ?? null,
                    );
                  },
                  overlaysData: [
                    ...processStatisticsStore.overlaysData,
                    ...processStatisticsBatchModificationStore.getOverlaysData({
                      sourceFlowNodeId: flowNodeId,
                      targetFlowNodeId: selectedTargetFlowNodeId ?? undefined,
                    }),
                  ],
                  // All flow nodes that can be a move modification target,
                  // except the source flow node
                  selectableFlowNodes: processXmlStore.selectableIds.filter(
                    (selectedFlowNodeId) =>
                      selectedFlowNodeId !== flowNodeId &&
                      selectedFlowNodeId !== undefined &&
                      isMoveModificationTarget(
                        processXmlStore.getFlowNode(selectedFlowNodeId),
                      ),
                  ),
                }
              : // Props for regular mode
                {
                  selectedFlowNodeIds: flowNodeId ? [flowNodeId] : undefined,
                  onFlowNodeSelection: (flowNodeId) => {
                    if (flowNodeId === null || flowNodeId === undefined) {
                      navigate(deleteSearchParams(location, ['flowNodeId']));
                    } else {
                      navigate(
                        setSearchParam(location, ['flowNodeId', flowNodeId]),
                      );
                    }
                  },
                  overlaysData: processStatisticsStore.overlaysData,
                  selectableFlowNodes: processXmlStore.selectableIds,
                })}
          >
            {statisticsOverlays?.map((overlay) => {
              const payload = overlay.payload as {
                flowNodeState: FlowNodeState;
                count: number;
              };

              return (
                <StateOverlay
                  testId={`state-overlay-${overlay.flowNodeId}-${payload.flowNodeState}`}
                  key={`${overlay.flowNodeId}-${payload.flowNodeState}`}
                  state={payload.flowNodeState}
                  count={payload.count}
                  container={overlay.container}
                />
              );
            })}
            {batchModificationBadgeOverlays?.map((overlay) => {
              const payload = overlay.payload as ModificationBadgePayload;
              return (
                <ModificationBadgeOverlay
                  key={overlay.flowNodeId}
                  container={overlay.container}
                  newTokenCount={payload.newTokenCount}
                  cancelledTokenCount={payload.cancelledTokenCount}
                />
              );
            })}
          </Diagram>
        )}
      </DiagramShell>
      {batchModificationStore.state.isEnabled && (
        <BatchModificationNotification
          sourceFlowNodeId={flowNodeId}
          targetFlowNodeId={selectedTargetFlowNodeId || undefined}
        />
      )}
    </Section>
  );
});

export {DiagramPanel};
