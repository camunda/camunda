/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import ErrorPage from './ErrorPage';

it('displays the error message passed in props', () => {
  const error = {
    errorMessage: 'error message hello'
  };
  const node = mount(<ErrorPage error={error} />);

  expect(node).toIncludeText('error message hello');
});
