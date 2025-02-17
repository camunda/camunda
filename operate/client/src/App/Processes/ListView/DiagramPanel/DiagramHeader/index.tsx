/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import isNil from 'lodash/isNil';
import {CopiableProcessID} from 'App/Processes/CopiableProcessID';
import {ProcessOperations} from '../../ProcessOperations';
import {Restricted} from 'modules/components/Restricted';
import {processesStore} from 'modules/stores/processes/processes.list';
import {
  PanelHeader,
  Description,
  DescriptionTitle,
  DescriptionData,
} from './styled';
import {Button} from '@carbon/react';
import {IncidentAlertingModal} from 'modules/components/IncidentAlertingModal';
import {useState} from 'react';

type ProcessDetails = {
  bpmnProcessId?: string;
  processName: string;
  version?: string;
  versionTag?: string | null;
};

type DiagramHeaderProps = {
  processDetails: ProcessDetails;
  processDefinitionId?: string;
  tenant?: string;
  isVersionSelected: boolean;
  panelHeaderRef?: React.RefObject<HTMLDivElement>;
};

const DiagramHeader: React.FC<DiagramHeaderProps> = observer(
  ({
    processDetails,
    processDefinitionId,
    tenant,
    isVersionSelected,
    panelHeaderRef,
  }) => {
    const {processName, bpmnProcessId, version, versionTag} = processDetails;
    const hasVersionTag = !isNil(versionTag);
    const hasSelectedProcess = bpmnProcessId !== undefined;

    const [isModalOpen, setIsModalOpen] = useState(false);

    return (
      <PanelHeader
        title={!hasSelectedProcess ? 'Process' : undefined}
        ref={panelHeaderRef}
      >
        {hasSelectedProcess && (
          <>
            <Description>
              <DescriptionTitle>Process name</DescriptionTitle>
              <DescriptionData title={processName} role="heading">
                {processName}
              </DescriptionData>
            </Description>

            <Description>
              <DescriptionTitle>Process ID</DescriptionTitle>
              <DescriptionData>
                <CopiableProcessID bpmnProcessId={bpmnProcessId} />
              </DescriptionData>
            </Description>

            {hasVersionTag && (
              <Description>
                <DescriptionTitle>Version tag</DescriptionTitle>
                <DescriptionData title={versionTag}>
                  {versionTag}
                </DescriptionData>
              </Description>
            )}
          </>
        )}

        {isVersionSelected && processDefinitionId !== undefined && (
          <Restricted
            scopes={['write']}
            resourceBasedRestrictions={{
              scopes: ['DELETE'],
              permissions: processesStore.getPermissions(bpmnProcessId, tenant),
            }}
          >
            {version !== undefined && (
              <ProcessOperations
                processDefinitionId={processDefinitionId}
                processName={processName}
                processVersion={version}
              />
            )}

            <Button
              kind="ghost"
              size="sm"
              onClick={() => setIsModalOpen(true)}
              title="Setup alert about incidents"
              aria-label="Setup alert about incidents"
            >
              Setup alert about incidents
            </Button>
            <IncidentAlertingModal
              isOpen={isModalOpen}
              onClose={() => setIsModalOpen(false)}
            />
          </Restricted>
        )}
      </PanelHeader>
    );
  },
);

export {DiagramHeader};
