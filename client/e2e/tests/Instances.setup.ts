/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {deploy, createSingleInstance} from '../setup-utils';
import {within, screen} from '@testing-library/testcafe';

const cmRunningInstancesCheckbox = within(
  screen.queryByTestId('filter-running-instances').shadowRoot()
).queryByRole('checkbox');

const cmActiveCheckbox = within(
  screen.queryByTestId('filter-active').shadowRoot()
).queryByRole('checkbox');

const cmIncidentsCheckbox = within(
  screen.queryByTestId('filter-incidents').shadowRoot()
).queryByRole('checkbox');

const cmFinishedInstancesCheckbox = within(
  screen.queryByTestId('filter-finished-instances').shadowRoot()
).queryByRole('checkbox');

const cmCompletedCheckbox = within(
  screen.queryByTestId('filter-completed').shadowRoot()
).queryByRole('checkbox');

const cmCanceledCheckbox = within(
  screen.queryByTestId('filter-canceled').shadowRoot()
).queryByRole('checkbox');

const setup = async () => {
  await deploy(['./e2e/tests/resources/orderProcess_v_1.bpmn']);

  const instanceWithoutAnIncident = await createSingleInstance(
    'orderProcess',
    1
  );

  await deploy(['./e2e/tests/resources/processWithAnIncident.bpmn']);

  const instanceWithAnIncident = await createSingleInstance(
    'processWithAnIncident',
    1
  );

  return {instanceWithoutAnIncident, instanceWithAnIncident};
};

export {
  setup,
  cmRunningInstancesCheckbox,
  cmActiveCheckbox,
  cmIncidentsCheckbox,
  cmFinishedInstancesCheckbox,
  cmCompletedCheckbox,
  cmCanceledCheckbox,
};
