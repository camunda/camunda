/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {runLastEffect} from '__mocks__/react';
import {shallow} from 'enzyme';

import TenantInfo from './TenantInfo';

jest.mock('config', () => ({areTenantsAvailable: jest.fn().mockReturnValue(false)}));

it('should not show anything if tenants are not available', async () => {
  const node = shallow(<TenantInfo tenant={{id: 'test'}} />);

  await runLastEffect();

  expect(node.find('.TenantInfo')).not.toExist();
});
