/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import {useUiConfig} from 'hooks';

import Footer from './Footer';

jest.mock('hooks', () => ({
  useUiConfig: jest.fn().mockReturnValue({optimizeVersion: ''}),
}));

it('includes the version number retrieved from back-end', async () => {
  const version = 'alpha';
  (useUiConfig as jest.Mock).mockReturnValue({optimizeVersion: version});

  const node = shallow(<Footer />);

  expect(node.find('.colophon')).toIncludeText(version);
});
