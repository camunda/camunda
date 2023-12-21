/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

  const processId = processesStore.getProcessId({process, tenant, version});

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
            selectableFlowNodes={processXmlStore.selectableIds}
            selectedFlowNodeIds={flowNodeId ? [flowNodeId] : undefined}
            onFlowNodeSelection={(flowNodeId) => {
              if (flowNodeId === null || flowNodeId === undefined) {
                navigate(deleteSearchParams(location, ['flowNodeId']));
              } else {
                navigate(setSearchParam(location, ['flowNodeId', flowNodeId]));
              }
            }}
            overlaysData={processStatisticsStore.overlaysData}
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
