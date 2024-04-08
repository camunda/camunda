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

function Mock({uiProperties}: {uiProperties: (keyof UiConfig)[]}) {
  const uiConfigValue = useUiConfig(...uiProperties);
  return <p>{JSON.stringify(uiConfigValue)}</p>;
}

it('should return the value of the uiConfig', async () => {
  (createAccessorFunction as jest.Mock).mockImplementationOnce(() => () => 'value');
  const node = shallow(<Mock uiProperties={['emailEnabled']} />);

  await runLastEffect();
  await node.update();

  expect(node.text()).toBe('{"emailEnabled":"value"}');
});

it('should return empty object if the uiConfig is not set', async () => {
  (createAccessorFunction as jest.Mock).mockImplementationOnce(() => () => undefined);
  const node = shallow(<Mock uiProperties={['emailEnabled']} />);

  await runLastEffect();
  await node.update();

  expect(node.text()).toBe('{}');
});

it('should return the value of the uiConfig for multiple keys', async () => {
  (createAccessorFunction as jest.Mock).mockImplementationOnce(() => () => 'value1');
  (createAccessorFunction as jest.Mock).mockImplementationOnce(() => () => 'value2');
  const node = shallow(<Mock uiProperties={['emailEnabled', 'enterpriseMode']} />);

  await runLastEffect();
  await node.update();

  expect(node.text()).toBe('{"emailEnabled":"value1","enterpriseMode":"value2"}');
});
