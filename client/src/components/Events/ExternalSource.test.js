/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import ExternalSource from './ExternalSource';

it('should match snapshot', () => {
  const node = shallow(<ExternalSource empty={false} />);

  expect(node).toMatchSnapshot();
});

it('display empty state with link to docs', () => {
  const node = shallow(<ExternalSource empty={true} />);

  expect(node).toMatchSnapshot();
});
