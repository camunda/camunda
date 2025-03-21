/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {DiagramShell} from 'modules/components/DiagramShell';
import {IncidentBanner, Section} from './styled';
import {DecisionViewer} from 'modules/components/DecisionViewer';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';
import {useDecisionDefinitionXmlOptions} from 'modules/queries/decisionDefinitions/useDecisionDefinitionXml';
import {useQuery} from '@tanstack/react-query';

const DecisionPanel: React.FC = observer(() => {
  const {decisionInstance} = decisionInstanceDetailsStore.state;
  const highlightableRules = Array.from(
    new Set(
      decisionInstanceDetailsStore.state.decisionInstance?.evaluatedOutputs.map(
        (output) => output.ruleIndex,
      ),
    ),
  ).filter((item) => item !== undefined);

  const {
    data: decisionDefinitionXml,
    isFetching,
    isError,
  } = useQuery(
    useDecisionDefinitionXmlOptions({
      decisionDefinitionKey: decisionInstance?.decisionDefinitionId,
      enabled: !!decisionInstance?.decisionId,
    }),
  );

  const getStatus = () => {
    if (isFetching) {
      return 'loading';
    }
    if (isError) {
      return 'error';
    }
    return 'content';
  };

  return (
    <Section
      data-testid="decision-panel"
      aria-label="decision panel"
      tabIndex={0}
    >
      {decisionInstance?.state === 'FAILED' && (
        <IncidentBanner data-testid="incident-banner">
          {decisionInstance.errorMessage}
        </IncidentBanner>
      )}
      <DiagramShell status={getStatus()}>
        <DecisionViewer
          xml={decisionDefinitionXml ?? null}
          decisionViewId={decisionInstance?.decisionId ?? null}
          highlightableRules={highlightableRules}
        />
      </DiagramShell>
    </Section>
  );
});

export {DecisionPanel};
