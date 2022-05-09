/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {addHandler} from 'request';

import WithLicense from './WithLicense';
import License from './License';

jest.mock('react', () => ({...jest.requireActual('react'), useEffect: (fn) => fn()}));

jest.mock('request', () => ({
  addHandler: jest.fn(),
  removeHandler: jest.fn(),
}));

it('should render child content', () => {
  const node = shallow(<WithLicense>child content</WithLicense>);

  const content = node.renderProp('render')();

  expect(content).toIncludeText('child content');
});

it('should should instead render the License page if a response has a 403 status and license errorCode', async () => {
  addHandler.mockClear();

  const node = shallow(<WithLicense>child content</WithLicense>);

  await addHandler.mock.calls[0][0]({
    status: 403,
    clone: () => ({json: () => ({errorCode: 'noLicenseStoredError'})}),
  });

  const content = node.renderProp('render')();

  expect(content).not.toIncludeText('child content');
  expect(content.find(License)).toExist();
});
