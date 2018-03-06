import React from 'react';
import {mount} from 'enzyme';

import DiagramBehavior from './DiagramBehavior';

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
      endEvent: 3
    },
    processInstanceCount: 5
  }
};

it('should add an end event overlay if an endEvent is selected', () => {
  mount(<DiagramBehavior {...props} endEvent={{id: 'endEvent'}} />);

  expect(viewer.add).toHaveBeenCalled();
  expect(viewer.add.mock.calls[0][0]).toBe('endEvent');
});

it('should add an overlay if an endEvent is hovered', () => {
  viewer.add.mockClear();

  mount(<DiagramBehavior {...props} hoveredNode={{
    id: 'otherEndEvent',
    $instanceOf: jest.fn().mockReturnValue(true)
  }} />);

  expect(viewer.add).toHaveBeenCalled();
  expect(viewer.add.mock.calls[0][0]).toBe('otherEndEvent');
});

it('should call the update hover state function', () => {
  viewer.on.mockClear();
  const spy = jest.fn();
  mount(<DiagramBehavior {...props} updateHover={spy} />);

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
  mount(<DiagramBehavior {...props} updateSelection={spy} />);

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

  mount(<DiagramBehavior {...props} updateSelection={spy} gateway={gateway.businessObject}  />);

  viewer.on.mock.calls.find(call => call[0] === 'element.click')[1]({element: gateway});
  expect(spy).toHaveBeenCalledWith('gateway', null);
});
