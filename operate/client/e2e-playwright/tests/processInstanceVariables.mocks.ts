/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {zeebeGrpcApi} from '../api/zeebe-grpc';

const {deployProcesses, createSingleInstance} = zeebeGrpcApi;

const setup = async () => {
  await deployProcesses(['onlyIncidentsProcess_v_1.bpmn']);
  const instance = await createSingleInstance('onlyIncidentsProcess', 1, {
    testData: 'something',
  });

  let variables: Record<string, string> = {};

  const alphabet = 'abcdefghijklmnopqrstuvwxyz'.split('');

  alphabet.forEach((letter1) => {
    alphabet.forEach((letter2) => {
      variables[`${letter1}${letter2}`] = `${letter1}${letter2}`;
    });
  });

  const instanceWithManyVariables = await createSingleInstance(
    'onlyIncidentsProcess',
    1,
    variables,
  );
  return {instance, instanceWithManyVariables};
};

export {setup};
