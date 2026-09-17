/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import Analysis from './Analysis';

it('should render the analysis routes without in-page tabs', () => {
  const node = shallow(<Analysis />);

  expect(node.find('Tabs')).toHaveLength(0);
  const routePaths = node.find('Route').map((route) => route.prop('path'));
  expect(routePaths).toEqual(
    expect.arrayContaining(['/analysis/taskAnalysis', '/analysis/branchAnalysis'])
  );
});
