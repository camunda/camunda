/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';
import {runLastEffect} from '__mocks__/react';

import {UiConfig, createAccessorFunction} from 'config';

import useUiConfig from './useUiConfig';

jest.mock('config', () => ({
  createAccessorFunction: jest.fn(() => () => null),
}));

function Mock({uiProperty}: {uiProperty: keyof UiConfig}) {
  const uiConfigValue = useUiConfig(uiProperty);
  return <p>{uiConfigValue?.toString()}</p>;
}

it('should return the value of the uiConfig', async () => {
  (createAccessorFunction as jest.Mock).mockImplementationOnce(() => () => 'value');
  const node = shallow(<Mock uiProperty="emailEnabled" />);

  runLastEffect();
  await node.update();

  expect(node.text()).toBe('value');
});

it('should return undefined if the uiConfig is not set', async () => {
  (createAccessorFunction as jest.Mock).mockImplementationOnce(() => () => undefined);
  const node = shallow(<Mock uiProperty="emailEnabled" />);

  runLastEffect();
  await node.update();

  expect(node.text()).toBe('');
});
