/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';

import DiagramBehavior from './DiagramBehavior';
jest.mock('react', () => ({...jest.requireActual('react-18'), useEffect: (fn) => fn()}));

const viewer = {
  get: jest.fn().mockReturnThis(),
  on: jest.fn(),
  addMarker: jest.fn(),
  removeMarker: jest.fn(),
  forEach: jest.fn(),
  clear: jest.fn(),
  add: jest.fn(),
  hasMarker: jest.fn(),
  viewbox: jest.fn().mockReturnValue({
    x: 50,
    y: 50,
    width: 50,
    height: 50,
  }),
  getGraphics: jest.fn().mockReturnValue({
    querySelector: jest.fn().mockReturnValue({
      setAttribute: jest.fn(),
      getAttribute: jest.fn().mockReturnValue(50),
    }),
  }),
};

const props = {
  viewer,
  data: {
    result: {
      data: [{key: 'endEvent', value: 3}],
      instanceCount: 5,
    },
  },
  setViewer: jest.fn(),
};

it('should add an end event overlay if an endEvent is selected', () => {
  shallow(<DiagramBehavior {...props} endEvent={{id: 'endEvent'}} />);

  expect(viewer.add).toHaveBeenCalled();
  expect(viewer.add.mock.calls[0][0]).toBe('endEvent');
});

it('should add an overlay if an endEvent is hovered', () => {
  viewer.add.mockClear();

  shallow(
    <DiagramBehavior
      {...props}
      hoveredNode={{
        id: 'otherEndEvent',
        $instanceOf: jest.fn().mockReturnValue(true),
      }}
    />
  );

  expect(viewer.add).toHaveBeenCalled();
  expect(viewer.add.mock.calls[0][0]).toBe('otherEndEvent');
});

it('should call the update hover state function', () => {
  viewer.on.mockClear();
  const spy = jest.fn();
  const node = shallow(<DiagramBehavior {...props} updateHover={spy} />);

  const elementObj = {
    $instanceOf: jest.fn().mockReturnValue(true),
  };

  node.find('ClickBehavior').prop('onHover')(elementObj);

  expect(spy).toHaveBeenCalledWith(elementObj);
});

it('should call the update selection state function', () => {
  viewer.on.mockClear();
  const spy = jest.fn();
  const node = shallow(<DiagramBehavior {...props} updateSelection={spy} />);

  const gateway = {
    businessObject: {
      $instanceOf: jest.fn().mockImplementation((type) => type === 'bpmn:Gateway'),
    },
  };
  const endEvent = {
    businessObject: {
      $instanceOf: jest.fn().mockImplementation((type) => type === 'bpmn:EndEvent'),
    },
  };

  node.find('ClickBehavior').prop('onClick')(gateway.businessObject);
  expect(spy).toHaveBeenCalledWith('gateway', gateway.businessObject);

  spy.mockClear();

  node.find('ClickBehavior').prop('onClick')(endEvent.businessObject);
  expect(spy).toHaveBeenCalledWith('endEvent', endEvent.businessObject);
});

it('should deselct a selected element when clicking on it', () => {
  viewer.on.mockClear();
  const spy = jest.fn();

  const gateway = {
    businessObject: {
      $instanceOf: jest.fn().mockImplementation((type) => type === 'bpmn:Gateway'),
    },
  };

  const node = shallow(
    <DiagramBehavior {...props} updateSelection={spy} gateway={gateway.businessObject} />
  );

  node.find('ClickBehavior').prop('onClick')(gateway.businessObject);
  expect(spy).toHaveBeenCalledWith('gateway', null);
});

it('should invoke setViewer function one time on load', () => {
  props.setViewer.mockClear();
  shallow(<DiagramBehavior {...props} />);

  expect(props.setViewer).toHaveBeenCalledTimes(1);
});
