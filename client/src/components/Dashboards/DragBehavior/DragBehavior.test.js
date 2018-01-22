import React from 'react';
import {mount} from 'enzyme';

import DragBehavior from './DragBehavior';

import {snapInPosition} from '../service';

jest.mock('../service', () => {return {
  snapInPosition: jest.fn().mockReturnValue({
    dimensions: {width: 3, height: 3},
    position: {x: 0, y: 0}
  }),
  collidesWithReport: jest.fn().mockReturnValue(false),
  applyPlacement: jest.fn()
}});

const props = {
  onDragStart: jest.fn(),
  onDragEnd: jest.fn(),
  updateReport: jest.fn(),
  tileDimensions: {
    columns: 5,
    outerWidth: 10,
    outerHeight: 10,
    innerWidth: 8
  },
  report: {
    position: {x: 0, y: 0},
    dimensions: {width: 1, height: 1}
  },
  reports: []
};

it('should call the dragStart callback when starting to drag', () => {
  const spy = jest.fn();
  const node = mount(<div className='DashboardObject'><DragBehavior onDragStart={spy} /></div>);

  node.find(DragBehavior).instance().startDragging({preventDefault: jest.fn()});

  expect(spy).toHaveBeenCalled();
});

it('should add dragging CSS class to surrounding dashboard object', () => {
  const spy = jest.fn();
  const node = mount(<div className='DashboardObject'><DragBehavior onDragStart={spy} /></div>);

  node.find(DragBehavior).instance().startDragging({preventDefault: jest.fn()});

  expect(node.getDOMNode().className).toContain('DragBehavior--dragging');
});

it('should update the x and y position of the report when dragging', () => {
  const spy = jest.fn();
  const node = mount(<div className='DashboardObject' style={{
    top: '10px',
    left: '10px'
  }}><DragBehavior onDragStart={spy} /></div>);

  node.find(DragBehavior).instance().startDragging({
    preventDefault: jest.fn(),
    screenX: 0,
    screenY: 0
  });

  node.find(DragBehavior).instance().saveMouseAndUpdateCardPosition({
    screenX: 5,
    screenY: 5
  });

  expect(node.getDOMNode().style.top).toBe('15px');
  expect(node.getDOMNode().style.left).toBe('15px');
});

it('should update the x and y position of the report when scrolling', () => {
  const spy = jest.fn();
  const node = mount(<div className='DashboardObject' style={{
    top: '10px',
    left: '10px'
  }}><DragBehavior onDragStart={spy} /></div>);
  window.pageYOffset = 0;
  node.find(DragBehavior).instance().startDragging({
    preventDefault: jest.fn(),
    screenX: 0,
    screenY: 0
  });

  window.pageYOffset = 5;
  node.find(DragBehavior).instance().updateCardPosition();

  expect(node.getDOMNode().style.top).toBe('15px');
  expect(node.getDOMNode().style.left).toBe('10px');
});

it('should call the update report callback on drop', () => {
  snapInPosition.mockReturnValueOnce({
    position: {x: 3, y: 1},
    dimensions: {width: 1, height: 1}
  });
  const spy = jest.fn();
  const node = mount(<div className='DashboardObject' style={{
    top: '0px',
    left: '0px'
  }}><DragBehavior {...props} updateReport={spy} /></div>);

  node.find(DragBehavior).instance().startDragging({
    preventDefault: jest.fn(),
    screenX: 0,
    screenY: 0
  });

  node.find(DragBehavior).instance().saveMouseAndUpdateCardPosition({
    screenX: 33,
    screenY: 8
  });

  node.find(DragBehavior).instance().stopDragging();

  expect(spy).toHaveBeenCalledWith({
    report: props.report,
    position: {
      x: 3,
      y: 1
    }
  });
});

it('should call the dragEnd callback on drop', () => {
  const spy = jest.fn();
  const node = mount(<div className='DashboardObject' style={{
    top: '0px',
    left: '0px'
  }}><DragBehavior {...props} onDragEnd={spy} /></div>);

  node.find(DragBehavior).instance().startDragging({
    preventDefault: jest.fn(),
    screenX: 0,
    screenY: 0
  });

  node.find(DragBehavior).instance().saveMouseAndUpdateCardPosition({
    screenX: 33,
    screenY: 8
  });

  node.find(DragBehavior).instance().stopDragging();

  expect(spy).toHaveBeenCalled();
});

it('should not update the report when it is dropped somewhere where is no space', () => {
  const spy = jest.fn();
  const node = mount(<div className='DashboardObject' style={{
    top: '0px',
    left: '0px'
  }}><DragBehavior {...props} reports={[
    {
      position: {x: 2, y: 0},
      dimensions: {width: 1, height: 4}
    },
    {
      position: {x: 3, y: 1},
      dimensions: {width: 2, height: 2}
    }
  ]} /></div>);

  node.find(DragBehavior).instance().startDragging({
    preventDefault: jest.fn(),
    screenX: 0,
    screenY: 0
  });

  node.find(DragBehavior).instance().saveMouseAndUpdateCardPosition({
    screenX: 20,
    screenY: 10
  });

  node.find(DragBehavior).instance().stopDragging();

  expect(spy).not.toHaveBeenCalled();
});
