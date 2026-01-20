/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {Button} from '@carbon/react';
import {ClassicBatch} from '@carbon/react/icons';
import {useNavigate} from 'react-router-dom';
import {CopiableProcessID} from 'App/Processes/CopiableProcessID';
import {ProcessOperations} from 'App/Processes/ListView/v2/ProcessOperations';
import {
  PanelHeader,
  Description,
  DescriptionTitle,
  DescriptionData,
  HeaderActions,
} from 'App/Processes/ListView/DiagramPanel/DiagramHeader/styled';
import {Paths} from 'modules/Routes';
import {
  getProcessDefinitionName,
  type ProcessDefinitionSelection,
} from 'modules/hooks/processDefinitions';

type DiagramHeaderProps = {
  processDefinitionSelection: ProcessDefinitionSelection;
  panelHeaderRef?: React.RefObject<HTMLDivElement | null>;
};

const DiagramHeader: React.FC<DiagramHeaderProps> = observer(
  ({processDefinitionSelection, panelHeaderRef}) => {
    const navigate = useNavigate();
    const title =
      processDefinitionSelection.kind === 'no-match' ? 'Process' : undefined;

    return (
      <PanelHeader title={title} ref={panelHeaderRef}>
        <DefinitionSelectionContent
          processDefinitionSelection={processDefinitionSelection}
        />
        <VersionTagContent
          processDefinitionSelection={processDefinitionSelection}
        />
        <HeaderActions>
          <ProcessOperationsContent
            processDefinitionSelection={processDefinitionSelection}
          />
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
        </HeaderActions>
      </PanelHeader>
    );
  },
);

function DefinitionSelectionContent(
  props: Pick<DiagramHeaderProps, 'processDefinitionSelection'>,
) {
  if (props.processDefinitionSelection.kind === 'no-match') {
    return null;
  }

  const definition = props.processDefinitionSelection.definition;
  const name = getProcessDefinitionName(definition);
  const definitionId = definition.processDefinitionId;

  return (
    <>
      <Description>
        <DescriptionTitle>Process name</DescriptionTitle>
        <DescriptionData title={name} role="heading">
          {name}
        </DescriptionData>
      </Description>

      <Description>
        <DescriptionTitle>Process ID</DescriptionTitle>
        <DescriptionData>
          <CopiableProcessID processDefinitionId={definitionId} />
        </DescriptionData>
      </Description>
    </>
  );
}

function VersionTagContent(
  props: Pick<DiagramHeaderProps, 'processDefinitionSelection'>,
) {
  if (props.processDefinitionSelection.kind !== 'single-version') {
    return null;
  }

  const definition = props.processDefinitionSelection.definition;
  const versionTag = definition.versionTag;

  if (!versionTag) {
    return null;
  }

  return (
    <Description>
      <DescriptionTitle>Version tag</DescriptionTitle>
      <DescriptionData title={versionTag}>{versionTag}</DescriptionData>
    </Description>
  );
}

function ProcessOperationsContent(
  props: Pick<DiagramHeaderProps, 'processDefinitionSelection'>,
) {
  if (props.processDefinitionSelection.kind !== 'single-version') {
    return null;
  }

  const definition = props.processDefinitionSelection.definition;
  const name = getProcessDefinitionName(definition);
  const definitionKey = definition.processDefinitionKey;
  const version = definition.version;

  return (
    <ProcessOperations
      processDefinitionKey={definitionKey}
      processName={name}
      processVersion={version}
    />
  );
}

export {DiagramHeader};
