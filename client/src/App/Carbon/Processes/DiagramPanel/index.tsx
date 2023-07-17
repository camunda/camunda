/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useOperationsPanelResize} from 'modules/hooks/useOperationsPanelResize';
import {useEffect, useRef} from 'react';
import {useLocation, useNavigate, Location} from 'react-router-dom';
import {
  deleteSearchParams,
  getProcessInstanceFilters,
} from 'modules/utils/filter';
import {COLLAPSABLE_PANEL_MIN_WIDTH} from 'modules/constants';
import {IS_PROCESS_DEFINITION_DELETION_ENABLED} from 'modules/feature-flags';
import {Restricted} from 'modules/components/Restricted';
import {processesStore} from 'modules/stores/processes';
import {ProcessOperations} from '../ProcessOperations';
import {PanelHeader, Section} from './styled';
import {DiagramShell} from 'modules/components/Carbon/DiagramShell';
import {Diagram} from 'modules/components/Carbon/Diagram';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {observer} from 'mobx-react';
import {StateOverlay} from 'modules/components/Carbon/StateOverlay';
import {processDiagramStore} from 'modules/stores/processDiagram';

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
  const {process, version, flowNodeId} = getProcessInstanceFilters(
    location.search,
  );

  const isVersionSelected = version !== undefined && version !== 'all';

  const selectedProcess = processesStore.state.processes.find(
    ({bpmnProcessId}) => bpmnProcessId === process,
  );

  const bpmnProcessId = selectedProcess?.bpmnProcessId;
  const processName = selectedProcess?.name ?? bpmnProcessId ?? 'Process';
  const isDiagramLoading =
    processDiagramStore.state.status === 'fetching' ||
    processesStore.state.status === 'initial' ||
    processesStore.state.status === 'fetching';

  const statisticsOverlays = diagramOverlaysStore.state.overlays.filter(
    ({type}) => type.match(/^statistics/) !== null,
  );

  const processId = processesStore.getProcessId(process, version);

  useEffect(() => {
    processDiagramStore.init();
    return () => {
      processDiagramStore.reset();
    };
  }, []);

  useEffect(() => {
    if (processId === undefined) {
      processDiagramStore.reset();
      return;
    }

    processDiagramStore.fetchProcessDiagram(processId);
  }, [processId, location.search]);

  const {xml} = processDiagramStore.state;

  const panelHeaderRef = useRef<HTMLDivElement>(null);

  useOperationsPanelResize(panelHeaderRef, (target, width) => {
    target.style[
      'marginRight'
    ] = `calc(${width}px - ${COLLAPSABLE_PANEL_MIN_WIDTH})`;
  });

  const getStatus = () => {
    if (isDiagramLoading) {
      return 'loading';
    }
    if (processDiagramStore.state.status === 'error') {
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
        {IS_PROCESS_DEFINITION_DELETION_ENABLED &&
          isVersionSelected &&
          processId !== undefined && (
            <Restricted
              scopes={['write']}
              resourceBasedRestrictions={{
                scopes: ['DELETE'],
                permissions: processesStore.getPermissions(bpmnProcessId),
              }}
            >
              <ProcessOperations
                processDefinitionId={processId}
                processName={processName}
                processVersion={version}
              />
            </Restricted>
          )}
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
            selectableFlowNodes={processDiagramStore.selectableIds}
            selectedFlowNodeId={flowNodeId}
            onFlowNodeSelection={(flowNodeId) => {
              if (flowNodeId === null || flowNodeId === undefined) {
                navigate(deleteSearchParams(location, ['flowNodeId']));
              } else {
                navigate(setSearchParam(location, ['flowNodeId', flowNodeId]));
              }
            }}
            overlaysData={processDiagramStore.overlaysData}
          >
            {statisticsOverlays?.map((overlay) => {
              const payload = overlay.payload as {
                flowNodeState: FlowNodeState;
                count: number;
              };

              return (
                <StateOverlay
                  key={`${overlay.flowNodeId}-${payload.flowNodeState}`}
                  state={payload.flowNodeState}
                  count={payload.count}
                  container={overlay.container}
                />
              );
            })}
          </Diagram>
        )}
      </DiagramShell>
    </Section>
  );
});

export {DiagramPanel};
