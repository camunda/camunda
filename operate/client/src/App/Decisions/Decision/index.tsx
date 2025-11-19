/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {useEffect, useState} from 'react';
import {useSearchParams} from 'react-router-dom';
import {DecisionOperations} from './DecisionOperations';
import {CopiableContent, PanelHeader, Section} from './styled';
import {DiagramShell} from 'modules/components/DiagramShell';
import {DecisionViewer} from 'modules/components/DecisionViewer';
import {notificationsStore} from 'modules/stores/notifications';
import {useDecisionDefinitionXmlOptions} from 'modules/queries/decisionDefinitions/useDecisionDefinitionXml';
import {useQuery} from '@tanstack/react-query';
import {panelStatesStore} from 'modules/stores/panelStates';
import {useSelectedDecisionDefinition} from 'modules/hooks/decisionDefinition';

const Decision: React.FC = observer(() => {
  const [params, setParams] = useSearchParams();
  const versionFilterValue = params.get('version');

  const {
    data: selectedDefinition,
    status,
    isFetching: isFetchingSelectedDecisionDefinition,
    isEnabled: isSelectedDecisionDefinitionEnabled,
    isError: isSelectedDecisionDefinitionError,
  } = useSelectedDecisionDefinition();
  const selectedDefinitionKey = selectedDefinition?.decisionDefinitionKey;
  const selectedDefinitionName = selectedDefinition?.name ?? 'Decision';
  const selectedDefinitionId = selectedDefinition?.decisionDefinitionId;

  useEffect(() => {
    if (status === 'success' && !selectedDefinitionKey) {
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
  }, [selectedDefinitionKey, status, setParams]);

  const {
    data: decisionDefinitionXml,
    isFetching,
    isFetched,
    isError,
  } = useQuery(
    useDecisionDefinitionXmlOptions({
      decisionDefinitionKey: selectedDefinitionKey ?? '',
      enabled: !!selectedDefinitionKey,
    }),
  );
  const [renderedDefinitionId, setRenderedDefinitionId] = useState<
    string | null
  >(null);

  useEffect(() => {
    if (isFetched && selectedDefinitionId) {
      setRenderedDefinitionId(selectedDefinitionId);
    }
  }, [isFetched, selectedDefinitionId]);

  const getDisplayStatus = () => {
    switch (true) {
      case isFetching || isFetchingSelectedDecisionDefinition:
        return 'loading';
      case isError || isSelectedDecisionDefinitionError:
        return 'error';
      case !isSelectedDecisionDefinitionEnabled:
        return 'empty';
      default:
        return 'content';
    }
  };

  return (
    <Section>
      <PanelHeader
        title={selectedDefinitionName}
        className={
          panelStatesStore.state.isOperationsCollapsed
            ? undefined
            : 'panelOffset'
        }
      >
        {selectedDefinition && (
          <>
            <CopiableContent
              copyButtonDescription="Decision ID / Click to copy"
              content={selectedDefinition.decisionDefinitionId}
            />
            <DecisionOperations
              decisionDefinitionKey={selectedDefinition.decisionDefinitionKey}
              decisionName={selectedDefinition.name}
              decisionVersion={selectedDefinition.version}
            />
          </>
        )}
      </PanelHeader>
      <DiagramShell
        status={getDisplayStatus()}
        emptyMessage={
          versionFilterValue === 'all'
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
          decisionViewId={renderedDefinitionId}
        />
      </DiagramShell>
    </Section>
  );
});

export {Decision};
