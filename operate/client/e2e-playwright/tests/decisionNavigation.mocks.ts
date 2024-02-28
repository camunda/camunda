/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  deployProcess,
  createSingleInstance,
  deployDecision,
} from '../setup-utils';

const setup = async () => {
  const decision = await deployDecision(['invoiceBusinessDecisions.dmn']);
  await deployProcess(['invoice.bpmn']);

  const processInstanceWithFailedDecision = await createSingleInstance(
    'invoice',
    1,
  );

  return {
    processInstanceWithFailedDecision,
    decisionKeys: (decision[0]?.deployments ?? [])
      //@ts-expect-error Property 'Metadata' does not exist on type 'DecisionDeployment'.ts(2339)
      .filter(({Metadata}) => Metadata === 'decision')
      .map(({decision}) => {
        return decision.decisionKey;
      }),
  };
};

export {setup};
