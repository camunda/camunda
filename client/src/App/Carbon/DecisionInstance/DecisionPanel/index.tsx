/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {observer} from 'mobx-react';
import {DiagramShell} from 'modules/components/Carbon/DiagramShell';
import {Section} from './styled';
import {DecisionViewer} from 'modules/components/Carbon/DecisionViewer';
import {decisionXmlStore} from 'modules/stores/decisionXml';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';

const DecisionPanel: React.FC = observer(() => {
  const {decisionInstance} = decisionInstanceDetailsStore.state;
  const highlightableRules = Array.from(
    new Set(
      decisionInstanceDetailsStore.state.decisionInstance?.evaluatedOutputs.map(
        (output) => output.ruleIndex,
      ),
    ),
  ).filter((item) => item !== undefined);

  const getStatus = () => {
    if (decisionXmlStore.state.status === 'fetching') {
      return 'loading';
    }
    if (decisionXmlStore.state.status === 'error') {
      return 'error';
    }
    return 'content';
  };

  return (
    <Section>
      <DiagramShell status={getStatus()}>
        <DecisionViewer
          xml={decisionXmlStore.state.xml}
          decisionViewId={decisionInstance?.decisionId ?? null}
          highlightableRules={highlightableRules}
        />
      </DiagramShell>
    </Section>
  );
});

export {DecisionPanel};
