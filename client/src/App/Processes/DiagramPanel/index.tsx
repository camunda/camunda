/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {SpinnerSkeleton} from 'modules/components/SpinnerSkeleton';
import {Diagram} from 'modules/components/Diagram';
import {
  DiagramContainer,
  DiagramEmptyMessage,
  Container,
  PanelHeader,
} from './styled';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {observer} from 'mobx-react';
import {StatusMessage} from 'modules/components/StatusMessage';
import {useLocation, useNavigate, Location} from 'react-router-dom';
import {
  getProcessInstanceFilters,
  deleteSearchParams,
} from 'modules/utils/filter';
import {processesStore} from 'modules/stores/processes';
import {StatisticsOverlay} from 'modules/components/StatisticsOverlay';
import {useEffect, useRef} from 'react';
import {processDiagramStore} from 'modules/stores/processDiagram';
import {Restricted} from 'modules/components/Restricted';
import {ProcessOperations} from '../ProcessOperations';
import {IS_PROCESS_DEFINITION_DELETION_ENABLED} from 'modules/feature-flags';
import {useOperationsPanelResize} from 'modules/hooks/useOperationsPanelResize';
import {COLLAPSABLE_PANEL_MIN_WIDTH} from 'modules/components/CollapsablePanel/styled';

type Props = {
  children: React.ReactNode;
};

const Message: React.FC<Props> = ({children}) => {
  return <DiagramEmptyMessage message={children} />;
};

function setSearchParam(
  location: Location,
  [key, value]: [key: string, value: string]
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
    location.search
  );
  const isNoProcessSelected =
    processDiagramStore.state.status !== 'error' && process === undefined;

  const isVersionSelected = version !== undefined && version !== 'all';

  const selectedProcess = processesStore.state.processes.find(
    ({bpmnProcessId}) => bpmnProcessId === process
  );

  const bpmnProcessId = selectedProcess?.bpmnProcessId;
  const processName = selectedProcess?.name || bpmnProcessId;
  const isDiagramLoading =
    processDiagramStore.state.status === 'fetching' ||
    processesStore.state.status === 'initial' ||
    processesStore.state.status === 'fetching';

  const statisticsOverlays = diagramOverlaysStore.state.overlays.filter(
    ({type}) => type.match(/^statistics/) !== null
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
    target.style['marginRight'] = `${width - COLLAPSABLE_PANEL_MIN_WIDTH}px`;
  });

  return (
    <Container>
      <PanelHeader title={processName ?? 'Process'} ref={panelHeaderRef}>
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
                processName={processName ?? 'Process'}
                processVersion={version}
              />
            </Restricted>
          )}
      </PanelHeader>

      <DiagramContainer>
        {isDiagramLoading ? (
          <SpinnerSkeleton data-testid="diagram-spinner" />
        ) : (
          processDiagramStore.state.status === 'error' && (
            <Message>
              <StatusMessage variant="error">
                Diagram could not be fetched
              </StatusMessage>
            </Message>
          )
        )}

        {isNoProcessSelected && (
          <Message>
            {
              'There is no Process selected\n To see a Diagram, select a Process in the Filters panel'
            }
          </Message>
        )}
        {!isVersionSelected && processName !== undefined ? (
          <Message>
            {`There is more than one Version selected for Process "${processName}"
               To see a Diagram, select a single Version`}
          </Message>
        ) : null}

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
                <StatisticsOverlay
                  key={`${overlay.flowNodeId}-${payload.flowNodeState}`}
                  flowNodeState={payload.flowNodeState}
                  count={payload.count}
                  container={overlay.container}
                />
              );
            })}
          </Diagram>
        )}
      </DiagramContainer>
    </Container>
  );
});

export {DiagramPanel};
