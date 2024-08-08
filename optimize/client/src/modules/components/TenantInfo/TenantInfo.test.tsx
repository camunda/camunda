/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
