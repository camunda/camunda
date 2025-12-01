/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import isNil from 'lodash/isNil';
import {useNavigate} from 'react-router-dom';
import {Button} from '@carbon/react';
import {ClassicBatch} from '@carbon/icons-react';
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
import {panelStatesStore} from 'modules/stores/panelStates';
import {Paths} from 'modules/Routes';

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
  panelHeaderRef?: React.RefObject<HTMLDivElement | null>;
};

const DiagramHeader: React.FC<DiagramHeaderProps> = observer(
  ({
    processDetails,
    processDefinitionId,
    tenant,
    isVersionSelected,
    panelHeaderRef,
  }) => {
    const navigate = useNavigate();
    const {processName, bpmnProcessId, version, versionTag} = processDetails;
    const hasVersionTag = !isNil(versionTag);
    const hasSelectedProcess = bpmnProcessId !== undefined;

    return (
      <PanelHeader
        title={!hasSelectedProcess ? 'Process' : undefined}
        ref={panelHeaderRef}
        className={
          panelStatesStore.state.isOperationsCollapsed
            ? undefined
            : 'panelOffset'
        }
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

        <div style={{marginLeft: 'auto', marginRight: 'var(--cds-spacing-04)', display: 'flex', alignItems: 'center', gap: 'var(--cds-spacing-04)'}}>
          <Button
            kind="tertiary"
            onClick={() => navigate(Paths.batchOperations())}
            iconDescription="View batch operations"
            renderIcon={ClassicBatch}
            title="View batch operations"
            aria-label="View batch operations"
            size="sm"
          >
            View batch operations
          </Button>

          {isVersionSelected && processDefinitionId !== undefined && (
            <Restricted
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
            </Restricted>
          )}
        </div>
      </PanelHeader>
    );
  },
);

export {DiagramHeader};
