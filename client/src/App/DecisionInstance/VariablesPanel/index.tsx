/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {observer} from 'mobx-react';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';
import {InputsAndOutputs} from './InputsAndOutputs';
import {Result} from './Result';
import {TabView} from 'modules/components/TabView';

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
              },
            ]),
        {
          id: 'result',
          label: 'Result',
          content: <Result />,
        },
      ]}
      eventName="variables-panel-used"
    />
  );
});

export {VariablesPanel};
