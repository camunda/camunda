/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import Analysis from './Analysis';

let mockNavV2Enabled = false;
jest.mock('feature-flags', () => ({
  get IS_NAV_V2_ENABLED() {
    return mockNavV2Enabled;
  },
}));

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useLocation: jest.fn().mockReturnValue({pathname: '/analysis/branchAnalysis'}),
}));

beforeEach(() => {
  mockNavV2Enabled = false;
});

it('should select the correct table', () => {
  const node = shallow(<Analysis />);

  expect(node.find('Tabs').prop('value')).toBe(1);
});

it('should drop the in-page tabs and render the analysis routes when nav V2 is enabled', () => {
  mockNavV2Enabled = true;

  const node = shallow(<Analysis />);

  expect(node.find('Tabs')).toHaveLength(0);
  const routePaths = node.find('Route').map((route) => route.prop('path'));
  expect(routePaths).toEqual(
    expect.arrayContaining(['/analysis/taskAnalysis', '/analysis/branchAnalysis'])
  );
});
