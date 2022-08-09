/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {deployProcess, createSingleInstance} from '../setup-utils';

const setup = async () => {
  await deployProcess(['onlyIncidentsProcess_v_1.bpmn']);
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
    variables
  );
  return {instance, instanceWithManyVariables};
};

export {setup};
