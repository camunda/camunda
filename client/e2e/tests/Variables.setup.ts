/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {deploy, createSingleInstance} from '../setup-utils';
import {screen, within} from '@testing-library/testcafe';

const cmNameField = screen.getByTestId('add-variable-name').shadowRoot();
const cmValueField = screen.getByTestId('add-variable-value').shadowRoot();

const cmVariableNameFilter = within(
  screen.queryByTestId('filter-variable-name').shadowRoot()
).queryByRole('textbox');

const cmVariableValueFilter = within(
  screen.queryByTestId('filter-variable-value').shadowRoot()
).queryByRole('textbox');

const cmEditValueField = screen.getByTestId('edit-variable-value').shadowRoot();

const setup = async () => {
  await deploy(['./e2e/tests/resources/onlyIncidentsProcess_v_1.bpmn']);
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

export {
  setup,
  cmNameField,
  cmValueField,
  cmEditValueField,
  cmVariableNameFilter,
  cmVariableValueFilter,
};
