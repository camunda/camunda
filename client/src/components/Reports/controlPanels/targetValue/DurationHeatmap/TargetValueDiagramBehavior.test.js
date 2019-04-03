/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import TargetValueDiagramBehavior from './TargetValueDiagramBehavior';

const viewer = {
  get: jest.fn().mockReturnThis(),
  on: jest.fn(),
  addMarker: jest.fn(),
  removeMarker: jest.fn(),
  forEach: jest.fn(),
  getGraphics: jest.fn().mockReturnValue({
    querySelector: jest.fn().mockReturnValue({
      setAttribute: jest.fn(),
      getAttribute: jest.fn().mockReturnValue(50)
    })
  })
};

it('should call the click handler with the selected node', () => {
  const spy = jest.fn();
  shallow(<TargetValueDiagramBehavior viewer={viewer} onClick={spy} nodeType="FlowNode" />);

  const element = {
    businessObject: {
      $instanceOf: jest.fn().mockImplementation(type => type === 'bpmn:FlowNode'),
      id: 'element_id'
    }
  };

  viewer.on.mock.calls.find(call => call[0] === 'element.click')[1]({element});
  expect(spy).toHaveBeenCalledWith('element_id');
});
