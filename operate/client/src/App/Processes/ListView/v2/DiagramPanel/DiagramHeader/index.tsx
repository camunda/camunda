/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {CopiableProcessID} from 'App/Processes/CopiableProcessID';
import {ProcessOperations} from '../../ProcessOperations';
import {
  PanelHeader,
  Description,
  DescriptionTitle,
  DescriptionData,
} from '../../../DiagramPanel/DiagramHeader/styled';
import {panelStatesStore} from 'modules/stores/panelStates';
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
    const title =
      processDefinitionSelection.kind === 'no-match' ? 'Process' : undefined;

    return (
      <PanelHeader
        title={title}
        ref={panelHeaderRef}
        className={
          panelStatesStore.state.isOperationsCollapsed
            ? undefined
            : 'panelOffset'
        }
      >
        <DefinitionSelectionContent
          processDefinitionSelection={processDefinitionSelection}
        />
        <SingleVersionSelectionContent
          processDefinitionSelection={processDefinitionSelection}
        />
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

function SingleVersionSelectionContent(
  props: Pick<DiagramHeaderProps, 'processDefinitionSelection'>,
) {
  if (props.processDefinitionSelection.kind !== 'single-version') {
    return null;
  }

  const definition = props.processDefinitionSelection.definition;
  const name = getProcessDefinitionName(definition);
  const definitionKey = definition.processDefinitionKey;
  const version = definition.version;
  const versionTag = definition.versionTag;

  return (
    <>
      {versionTag && (
        <Description>
          <DescriptionTitle>Version tag</DescriptionTitle>
          <DescriptionData title={versionTag}>{versionTag}</DescriptionData>
        </Description>
      )}
      <ProcessOperations
        processDefinitionKey={definitionKey}
        processName={name}
        processVersion={version}
      />
    </>
  );
}

export {DiagramHeader};
