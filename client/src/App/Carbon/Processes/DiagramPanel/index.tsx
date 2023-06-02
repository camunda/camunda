/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useOperationsPanelResize} from 'modules/hooks/useOperationsPanelResize';
import {useRef} from 'react';
import {useLocation} from 'react-router-dom';
import {getProcessInstanceFilters} from 'modules/utils/filter';
import {COLLAPSABLE_PANEL_MIN_WIDTH} from 'modules/constants';
import {IS_PROCESS_DEFINITION_DELETION_ENABLED} from 'modules/feature-flags';
import {Restricted} from 'modules/components/Restricted';
import {processesStore} from 'modules/stores/processes';
import {ProcessOperations} from '../ProcessOperations';
import {PanelHeader, Section} from './styled';
import {DiagramShell} from 'modules/components/Carbon/DiagramShell';

const DiagramPanel: React.FC = () => {
  const location = useLocation();
  const {process, version} = getProcessInstanceFilters(location.search);

  const isVersionSelected = version !== undefined && version !== 'all';

  const selectedProcess = processesStore.state.processes.find(
    ({bpmnProcessId}) => bpmnProcessId === process
  );

  const bpmnProcessId = selectedProcess?.bpmnProcessId;
  const processName = selectedProcess?.name ?? bpmnProcessId ?? 'Process';

  const panelHeaderRef = useRef<HTMLDivElement>(null);

  const processId = processesStore.getProcessId(process, version);

  useOperationsPanelResize(panelHeaderRef, (target, width) => {
    target.style[
      'marginRight'
    ] = `calc(${width}px - ${COLLAPSABLE_PANEL_MIN_WIDTH})`;
  });

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
        status={isVersionSelected ? 'content' : 'empty'}
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
        processes - diagram
      </DiagramShell>
    </Section>
  );
};

export {DiagramPanel};
