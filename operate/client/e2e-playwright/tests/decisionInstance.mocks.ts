/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {zeebeGrpcApi} from '../api/zeebe-grpc';

const {deployDecisions} = zeebeGrpcApi;

const setup = async () => {
  const {deployments} = await deployDecisions(['decisions_v_2.dmn']);

  const decision1Key = deployments[0]?.decision.decisionKey;
  const decision2Key = deployments[1]?.decision.decisionKey;

  return {
    decision1Key,
    decision2Key,
  };
};

export {setup};
