/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {getDiagramElementsBetween} from 'services';

import DiagramBehavior from './DiagramBehavior';

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    getDiagramElementsBetween: jest.fn()
  };
});

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
    height: 50
  }),
  getGraphics: jest.fn().mockReturnValue({
    querySelector: jest.fn().mockReturnValue({
      setAttribute: jest.fn(),
      getAttribute: jest.fn().mockReturnValue(50)
    })
  })
};

const props = {
  viewer,
  data: {
    result: {
      data: [{key: 'endEvent', value: 3}],
      processInstanceCount: 5
    }
  },
  setViewer: jest.fn()
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
        $instanceOf: jest.fn().mockReturnValue(true)
      }}
    />
  );

  expect(viewer.add).toHaveBeenCalled();
  expect(viewer.add.mock.calls[0][0]).toBe('otherEndEvent');
});

it('should call the update hover state function', () => {
  viewer.on.mockClear();
  const spy = jest.fn();
  shallow(<DiagramBehavior {...props} updateHover={spy} />);

  const element = {
    businessObject: {
      $instanceOf: jest.fn().mockReturnValue(true)
    }
  };

  viewer.on.mock.calls.find(call => call[0] === 'element.hover')[1]({element});

  expect(spy).toHaveBeenCalledWith(element.businessObject);
});

it('should call the update selection state function', () => {
  viewer.on.mockClear();
  const spy = jest.fn();
  shallow(<DiagramBehavior {...props} updateSelection={spy} />);

  const gateway = {
    businessObject: {
      $instanceOf: jest.fn().mockImplementation(type => type === 'bpmn:Gateway')
    }
  };
  const endEvent = {
    businessObject: {
      $instanceOf: jest.fn().mockImplementation(type => type === 'bpmn:EndEvent')
    }
  };

  viewer.on.mock.calls.find(call => call[0] === 'element.click')[1]({element: gateway});
  expect(spy).toHaveBeenCalledWith('gateway', gateway.businessObject);

  spy.mockClear();

  viewer.on.mock.calls.find(call => call[0] === 'element.click')[1]({element: endEvent});
  expect(spy).toHaveBeenCalledWith('endEvent', endEvent.businessObject);
});

it('should deselct a selected element when clicking on it', () => {
  viewer.on.mockClear();
  const spy = jest.fn();

  const gateway = {
    businessObject: {
      $instanceOf: jest.fn().mockImplementation(type => type === 'bpmn:Gateway')
    }
  };

  shallow(<DiagramBehavior {...props} updateSelection={spy} gateway={gateway.businessObject} />);

  viewer.on.mock.calls.find(call => call[0] === 'element.click')[1]({element: gateway});
  expect(spy).toHaveBeenCalledWith('gateway', null);
});

it('should invoke setViewer function one time on load', () => {
  props.setViewer.mockClear();
  shallow(<DiagramBehavior {...props} />);

  expect(props.setViewer).toHaveBeenCalledTimes(1);
});

it('should highlight elements between the selected gateway and end event', () => {
  getDiagramElementsBetween.mockReturnValue(['a', 'b', 'c']);
  shallow(<DiagramBehavior {...props} gateway="1" endEvent="2" />);

  expect(getDiagramElementsBetween).toHaveBeenCalledWith('1', '2', viewer);

  expect(viewer.addMarker).toHaveBeenCalledWith('a', 'analysis-part-highlight');
  expect(viewer.addMarker).toHaveBeenCalledWith('b', 'analysis-part-highlight');
  expect(viewer.addMarker).toHaveBeenCalledWith('c', 'analysis-part-highlight');
});
