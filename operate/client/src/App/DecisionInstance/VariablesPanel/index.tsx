/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';
import {TabView} from 'modules/components/TabView';
import {InputsAndOutputs} from './InputsAndOutputs';
import {Result} from './Result';

const VariablesPanel: React.FC = observer(() => {
  const isLiteralExpression =
    decisionInstanceDetailsStore.state.decisionInstance?.decisionType ===
    'LITERAL_EXPRESSION';

  return (
    <TabView
      dataTestId="decision-instance-variables-panel"
      tabs={[
        ...(isLiteralExpression
          ? []
          : [
              {
                id: 'inputs-and-outputs',
                label: 'Inputs and Outputs',
                content: <InputsAndOutputs />,
                removePadding: true,
              },
            ]),
        {
          id: 'result',
          label: 'Result',
          content: <Result />,
          removePadding: true,
        },
      ]}
      eventName="variables-panel-used"
    />
  );
});

export {VariablesPanel};
