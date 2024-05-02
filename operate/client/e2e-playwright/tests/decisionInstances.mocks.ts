/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {deployDecision} from '../setup-utils';

const setup = async () => {
  const decisionV1 = await deployDecision(['decisions_v_1.dmn']);
  const decisionV2 = await deployDecision(['decisions_v_2.dmn']);

  return {
    decisionKeys: [
      ...(decisionV1[0]?.deployments ?? [])
        //@ts-expect-error Property 'Metadata' does not exist on type 'DecisionDeployment'.ts(2339)
        .filter(({Metadata}) => Metadata === 'decision')
        .map(({decision}) => {
          return decision.decisionKey;
        }),
      ...(decisionV2[0]?.deployments ?? [])
        //@ts-expect-error Property 'Metadata' does not exist on type 'DecisionDeployment'.ts(2339)
        .filter(({Metadata}) => Metadata === 'decision')
        .map(({decision}) => {
          return decision.decisionKey;
        }),
    ],
  };
};

export {setup};
