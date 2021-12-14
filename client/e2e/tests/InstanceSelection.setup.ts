/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {deploy, createInstances} from '../setup-utils';
import {within, screen} from '@testing-library/testcafe';

const cmFinishedInstancesCheckbox = within(
  screen.queryByTestId('filter-finished-instances').shadowRoot()
).queryByRole('checkbox');

const cmInstanceIdsField = within(
  screen.queryByTestId('filter-instance-ids').shadowRoot()
).queryByRole('textbox');

const setup = async () => {
  await deploy([
    './e2e/tests/resources/withoutIncidentsProcess_v_1.bpmn',
    './e2e/tests/resources/processWithAnIncident.bpmn',
    './e2e/tests/resources/orderProcess_v_1.bpmn',
  ]);

  await createInstances('withoutIncidentsProcess', 1, 10);
  await createInstances('orderProcess', 1, 10);
  await createInstances('processWithAnIncident', 1, 5);
};

export {setup, cmFinishedInstancesCheckbox, cmInstanceIdsField};
