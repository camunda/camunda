/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {deploy, createSingleInstance} from '../setup-utils';

const setup = async () => {
  await deploy(['invoice.bpmn']);

  const processInstanceWithFailedDecision = await createSingleInstance(
    'invoice',
    1
  );

  return {
    processInstanceWithFailedDecision,
  };
};

export {setup};
