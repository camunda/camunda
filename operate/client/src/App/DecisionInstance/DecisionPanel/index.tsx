/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {DiagramShell} from 'modules/components/DiagramShell';
import {IncidentBanner, Section} from './styled';
import {DecisionViewer} from 'modules/components/DecisionViewer';
import {useDecisionDefinitionXml} from 'modules/queries/decisionDefinitions/useDecisionDefinitionXml';
import {HTTP_STATUS_FORBIDDEN} from 'modules/constants/statusCode';
import {useDecisionInstance} from 'modules/queries/decisionInstances/useDecisionInstance';
import {useMemo} from 'react';

type DecisionPanelProps = {
  decisionEvaluationInstanceKey: string;
};

const DecisionPanel: React.FC<DecisionPanelProps> = (props) => {
  const {data: decisionInstance} = useDecisionInstance(
    props.decisionEvaluationInstanceKey,
  );
  const highlightableRules = useMemo(() => {
    if (!decisionInstance?.matchedRules) {
      return [];
    }

    return Array.from(
      new Set(decisionInstance.matchedRules.map((rule) => rule.ruleIndex)),
    );
  }, [decisionInstance?.matchedRules]);

  const {
    data: decisionDefinitionXml,
    isFetching,
    isError,
    error,
  } = useDecisionDefinitionXml({
    decisionDefinitionKey: decisionInstance?.decisionDefinitionKey,
  });

  const getStatus = () => {
    if (isFetching) {
      return 'loading';
    }
    if (error?.response?.status === HTTP_STATUS_FORBIDDEN) {
      return 'forbidden';
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
          {decisionInstance.evaluationFailure}
        </IncidentBanner>
      )}
      <DiagramShell status={getStatus()}>
        <DecisionViewer
          xml={decisionDefinitionXml ?? null}
          decisionViewId={decisionInstance?.decisionDefinitionId ?? null}
          highlightableRules={highlightableRules}
        />
      </DiagramShell>
    </Section>
  );
};

export {DecisionPanel};
