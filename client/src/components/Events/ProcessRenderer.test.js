/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ProcessRenderer from './ProcessRenderer';
import Modeler from 'bpmn-js/lib/Modeler';

it('should provide a getXml action', () => {
  const viewer = new Modeler();
  const getXml = {};

  const props = {
    viewer,
    getXml,
    name: 'some name',
    onChange: jest.fn
  };

  shallow(<ProcessRenderer {...props} />);

  expect(typeof getXml.action).toBe('function');
});
