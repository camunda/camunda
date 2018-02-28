import React from 'react';
import {mount} from 'enzyme';

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
  mount(<TargetValueDiagramBehavior viewer={viewer} onClick={spy} />);

  const element = {
    businessObject: {
      $instanceOf: jest.fn().mockImplementation(type => type === 'bpmn:FlowNode'),
      id: 'element_id'
    }
  };

  viewer.on.mock.calls.find(call => call[0] === 'element.click')[1]({element});
  expect(spy).toHaveBeenCalledWith('element_id');
});
