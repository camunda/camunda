/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useState} from 'react';
import {TabView} from 'modules/components/TabView';
import {InputsAndOutputs} from './InputsAndOutputs';
import {Result} from './Result';
import type {DecisionInstance} from '@camunda/camunda-api-zod-schemas/8.10';

type VariablesPanelProps = {
  decisionEvaluationInstanceKey: DecisionInstance['decisionEvaluationInstanceKey'];
  decisionDefinitionType: DecisionInstance['decisionDefinitionType'];
};

const VariablesPanel: React.FC<VariablesPanelProps> = ({
  decisionEvaluationInstanceKey,
  decisionDefinitionType,
}) => {
  const [lastSelectedTabId, setLastSelectedTabId] = useState<
    string | undefined
  >(undefined);

  const tabs = useMemo(() => {
    let tabs: React.ComponentProps<typeof TabView>['tabs'] = [
      {
        id: 'result',
        label: 'Result',
        content: (
          <Result
            decisionEvaluationInstanceKey={decisionEvaluationInstanceKey}
          />
        ),
        removePadding: true,
      },
    ];

    if (decisionDefinitionType !== 'LITERAL_EXPRESSION') {
      tabs.unshift({
        id: 'inputs-and-outputs',
        label: 'Inputs and Outputs',
        content: (
          <InputsAndOutputs
            decisionEvaluationInstanceKey={decisionEvaluationInstanceKey}
          />
        ),
        removePadding: true,
      });
    }

    return tabs;
  }, [decisionDefinitionType, decisionEvaluationInstanceKey]);

  // Preserve the previously selected tab (e.g. "Result") when switching
  // between decisions. Left undefined until the user actually picks a tab,
  // so the default tab order (Inputs and Outputs first) is unaffected; once
  // set, falls back to letting TabView pick the default again if the newly
  // selected decision doesn't have that tab (e.g. a literal expression
  // decision, which has no "Inputs and Outputs" tab).
  const activeTabId =
    lastSelectedTabId !== undefined &&
    tabs.some(({id}) => id === lastSelectedTabId)
      ? lastSelectedTabId
      : undefined;

  return (
    <TabView
      dataTestId="decision-instance-variables-panel"
      tabs={tabs}
      eventName="variables-panel-used"
      activeTabId={activeTabId}
      onTabChange={setLastSelectedTabId}
    />
  );
};

export {VariablesPanel};
