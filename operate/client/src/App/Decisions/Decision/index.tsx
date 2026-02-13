/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {useEffect} from 'react';
import {useSearchParams} from 'react-router-dom';
import {DecisionOperations} from './DecisionOperations';
import {CopiableContent, PanelHeader, Section} from './styled';
import {DiagramShell} from 'modules/components/DiagramShell';
import {DecisionViewer} from 'modules/components/DecisionViewer';
import {notificationsStore} from 'modules/stores/notifications';
import {useDecisionDefinitionXml} from 'modules/queries/decisionDefinitions/useDecisionDefinitionXml';
import {useDecisionDefinitionSelection} from 'modules/hooks/decisionDefinition';

const Decision: React.FC = observer(() => {
  const [_, setParams] = useSearchParams();

  const {
    data: definitionSelection = {kind: 'no-match'},
    status: definitionSelectionStatus,
    isLoading: isDefinitionSelectionLoading,
    isEnabled: isDefinitionSelectionEnabled,
    isError: isDefinitionSelectionError,
  } = useDecisionDefinitionSelection();
  const selectedDefinitionKey =
    definitionSelection.kind === 'single-version'
      ? definitionSelection.definition.decisionDefinitionKey
      : undefined;
  const selectedDefinitionId =
    definitionSelection.kind === 'single-version'
      ? definitionSelection.definition.decisionDefinitionId
      : undefined;
  const selectedDefinitionName =
    definitionSelection.kind !== 'no-match'
      ? definitionSelection.definition.name
      : 'Decision';

  useEffect(() => {
    if (
      definitionSelectionStatus === 'success' &&
      definitionSelection.kind === 'no-match'
    ) {
      setParams((p) => {
        p.delete('name');
        p.delete('version');
        return p;
      });
      notificationsStore.displayNotification({
        kind: 'error',
        title: 'Decision could not be found',
        isDismissable: true,
      });
    }
  }, [definitionSelection, definitionSelectionStatus, setParams]);

  const {
    data: decisionDefinitionXml,
    isFetching,
    isError,
  } = useDecisionDefinitionXml({decisionDefinitionKey: selectedDefinitionKey});

  const getDisplayStatus = () => {
    switch (true) {
      case isFetching || isDefinitionSelectionLoading:
        return 'loading';
      case isError || isDefinitionSelectionError:
        return 'error';
      case !isDefinitionSelectionEnabled ||
        definitionSelection.kind !== 'single-version':
        return 'empty';
      default:
        return 'content';
    }
  };

  return (
    <Section>
      <PanelHeader title={selectedDefinitionName}>
        {definitionSelection.kind !== 'no-match' && (
          <CopiableContent
            copyButtonDescription="Decision ID / Click to copy"
            content={definitionSelection.definition.decisionDefinitionId}
          />
        )}
        {definitionSelection.kind === 'single-version' && (
          <DecisionOperations
            decisionRequirementsKey={
              definitionSelection.definition.decisionRequirementsKey
            }
            decisionName={definitionSelection.definition.name}
            decisionVersion={definitionSelection.definition.version}
          />
        )}
      </PanelHeader>
      <DiagramShell
        status={getDisplayStatus()}
        emptyMessage={
          definitionSelection.kind === 'all-versions'
            ? {
                message: `There is more than one Version selected for Decision "${selectedDefinitionName}"`,
                additionalInfo:
                  'To see a Decision Table or a Literal Expression, select a single Version',
              }
            : {
                message: 'There is no Decision selected',
                additionalInfo:
                  'To see a Decision Table or a Literal Expression, select a Decision in the Filters panel',
              }
        }
      >
        <DecisionViewer
          xml={decisionDefinitionXml ?? null}
          decisionViewId={selectedDefinitionId ?? null}
        />
      </DiagramShell>
    </Section>
  );
});

export {Decision};
