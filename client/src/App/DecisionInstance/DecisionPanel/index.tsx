/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {observer} from 'mobx-react';
import {decisionXmlStore} from 'modules/stores/decisionXml';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';
import {DecisionViewer} from 'modules/components/DecisionViewer';
import {IncidentBanner, Container} from './styled';

const DecisionPanel: React.FC = observer(() => {
  const {decisionInstance} = decisionInstanceDetailsStore.state;
  const highlightableRules = Array.from(
    new Set(
      decisionInstanceDetailsStore.state.decisionInstance?.evaluatedOutputs.map(
        (output) => output.ruleIndex
      )
    )
  ).filter((item) => item !== undefined);

  return (
    <Container data-testid="decision-panel">
      {decisionInstance?.state === 'FAILED' && (
        <IncidentBanner data-testid="incident-banner">
          {decisionInstance.errorMessage}
        </IncidentBanner>
      )}
      <DecisionViewer
        xml={decisionXmlStore.state.xml}
        decisionViewId={decisionInstance?.decisionId ?? null}
        highlightableRules={highlightableRules}
      />
    </Container>
  );
});

export {DecisionPanel};
