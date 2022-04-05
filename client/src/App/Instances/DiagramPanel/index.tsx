/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {SpinnerSkeleton} from 'modules/components/SpinnerSkeleton';
import DiagramLegacy, {Diagram} from 'modules/components/Diagram';
import {DiagramContainer, DiagramEmptyMessage, Container} from './styled';
import {instancesDiagramStore} from 'modules/stores/instancesDiagram';
import {processStatisticsStore} from 'modules/stores/processStatistics';
import {observer} from 'mobx-react';
import {StatusMessage} from 'modules/components/StatusMessage';
import {useLocation, useNavigate} from 'react-router-dom';
import {Location} from 'history';
import {
  getProcessInstanceFilters,
  deleteSearchParams,
} from 'modules/utils/filter';
import {processesStore} from 'modules/stores/processes';
import {IS_NEXT_DIAGRAM} from 'modules/feature-flags';
import {PanelHeader} from 'modules/components/PanelHeader';

const Message: React.FC = ({children}) => {
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
  const {status, diagramModel, xml} = instancesDiagramStore.state;
  const {selectableIds} = instancesDiagramStore;
  const {statistics} = processStatisticsStore.state;
  const {process, version, flowNodeId} = getProcessInstanceFilters(
    location.search
  );
  const isNoProcessSelected = status !== 'error' && process === undefined;
  const isNoVersionSelected = status !== 'error' && version === 'all';

  const selectedProcess = processesStore.state.processes.find(
    ({bpmnProcessId}) => bpmnProcessId === process
  );

  const processName = selectedProcess?.name || selectedProcess?.bpmnProcessId;
  const isDiagramLoading =
    processStatisticsStore.state.isLoading ||
    status === 'fetching' ||
    processesStore.state.status === 'initial' ||
    processesStore.state.status === 'fetching';

  return (
    <Container>
      <PanelHeader title={processName ?? 'Process'} />

      <DiagramContainer>
        {isDiagramLoading ? (
          <SpinnerSkeleton data-testid="diagram-spinner" />
        ) : (
          status === 'error' && (
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
        {isNoVersionSelected && processName !== undefined ? (
          <Message>
            {`There is more than one Version selected for Process "${processName}"
               To see a Diagram, select a single Version`}
          </Message>
        ) : null}

        {IS_NEXT_DIAGRAM ? (
          xml !== null && (
            <Diagram
              xml={xml}
              selectableFlowNodes={selectableIds}
              selectedFlowNodeId={flowNodeId}
              onFlowNodeSelection={(flowNodeId) => {
                if (flowNodeId === null || flowNodeId === undefined) {
                  navigate(deleteSearchParams(location, ['flowNodeId']));
                } else {
                  navigate(
                    setSearchParam(location, ['flowNodeId', flowNodeId])
                  );
                }
              }}
            />
          )
        ) : // @ts-expect-error ts-migrate(2339) FIXME: Property 'definitions' does not exist on type 'nev... Remove this comment to see the full error message
        !isNoVersionSelected && diagramModel?.definitions ? (
          <DiagramLegacy
            // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
            definitions={diagramModel.definitions}
            onFlowNodeSelection={(flowNodeId) => {
              if (flowNodeId === null || flowNodeId === undefined) {
                navigate(deleteSearchParams(location, ['flowNodeId']));
              } else {
                navigate(setSearchParam(location, ['flowNodeId', flowNodeId]));
              }
            }}
            flowNodesStatistics={statistics}
            selectedFlowNodeId={flowNodeId}
            selectableFlowNodes={selectableIds}
          />
        ) : null}
      </DiagramContainer>
    </Container>
  );
});

export {DiagramPanel};
