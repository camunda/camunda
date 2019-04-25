/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import ResizeHandle from './ResizeHandle';

import {snapInPosition} from '../service';

jest.mock('../service', () => {
  return {
    snapInPosition: jest.fn().mockReturnValue({
      dimensions: {width: 3, height: 3},
      position: {x: 0, y: 0}
    }),
    collidesWithReport: jest.fn().mockReturnValue(false),
    applyPlacement: jest.fn()
  };
});

const props = {
  onResizeStart: jest.fn(),
  onResizeEnd: jest.fn(),
  updateReport: jest.fn(),
  tileDimensions: {
    columns: 5,
    outerWidth: 10,
    outerHeight: 10,
    innerWidth: 9,
    innerHeight: 9
  },
  report: {
    position: {x: 0, y: 0},
    dimensions: {width: 0, height: 0}
  },
  reports: []
};

it('should call the resizeStart callback when starting to drag', () => {
  const spy = jest.fn();
  const node = mount(
    <main>
      <div className="DashboardRenderer">
        <div className="DashboardObject">
          <ResizeHandle onResizeStart={spy} />
        </div>
      </div>
    </main>
  );

  node
    .find(ResizeHandle)
    .instance()
    .startDragging({preventDefault: jest.fn()});

  expect(spy).toHaveBeenCalled();
});

it('should add dragging CSS class to surrounding dashboard object', () => {
  const spy = jest.fn();
  const node = mount(
    <main>
      <div className="DashboardRenderer">
        <div className="DashboardObject">
          <ResizeHandle onResizeStart={spy} />
        </div>
      </div>
    </main>
  );

  node
    .find(ResizeHandle)
    .instance()
    .startDragging({preventDefault: jest.fn()});

  expect(node.find('.DashboardObject').getDOMNode().className).toContain('ResizeHandle--dragging');
});

it('should update the width and height of the report when dragging', () => {
  const spy = jest.fn();
  const node = mount(
    <main>
      <div className="DashboardRenderer">
        <div
          className="DashboardObject"
          style={{
            width: '10px',
            height: '10px'
          }}
        >
          <ResizeHandle {...props} onResizeStart={spy} />
        </div>
      </div>
    </main>
  );

  node
    .find(ResizeHandle)
    .instance()
    .startDragging({
      preventDefault: jest.fn(),
      screenX: 0,
      screenY: 0
    });

  node
    .find(ResizeHandle)
    .instance()
    .saveMouseAndUpdateCardSize({
      screenX: 5,
      screenY: 5
    });

  expect(node.find('.DashboardObject').getDOMNode().style.width).toBe('15px');
  expect(node.find('.DashboardObject').getDOMNode().style.height).toBe('15px');
});

it('should update the width and height of the report when scrolling', () => {
  const spy = jest.fn();
  const node = mount(
    <main>
      <div className="DashboardRenderer">
        <div
          className="DashboardObject"
          style={{
            width: '10px',
            height: '10px'
          }}
        >
          <ResizeHandle {...props} onResizeStart={spy} />
        </div>
      </div>
    </main>
  );

  node.getDOMNode().scrollTop = 0;
  node
    .find(ResizeHandle)
    .instance()
    .startDragging({
      preventDefault: jest.fn(),
      screenX: 0,
      screenY: 0
    });

  node.getDOMNode().scrollTop = 5;
  node
    .find(ResizeHandle)
    .instance()
    .updateCardSize();

  expect(node.find('.DashboardObject').getDOMNode().style.width).toBe('10px');
  expect(node.find('.DashboardObject').getDOMNode().style.height).toBe('15px');
});

it('should call the update report callback on drop', () => {
  snapInPosition.mockReturnValue({
    position: {x: 1, y: 1},
    dimensions: {width: 3, height: 1}
  });

  const spy = jest.fn();
  const node = mount(
    <main>
      <div className="DashboardRenderer">
        <div
          className="DashboardObject"
          style={{
            width: '0px',
            height: '0px'
          }}
        >
          <ResizeHandle {...props} updateReport={spy} />
        </div>
      </div>
    </main>
  );

  node
    .find(ResizeHandle)
    .instance()
    .startDragging({
      preventDefault: jest.fn(),
      screenX: 0,
      screenY: 0
    });

  node
    .find(ResizeHandle)
    .instance()
    .saveMouseAndUpdateCardSize({
      screenX: 33,
      screenY: 8
    });

  node
    .find(ResizeHandle)
    .instance()
    .stopDragging();

  expect(spy).toHaveBeenCalledWith({
    report: props.report,
    dimensions: {
      width: 3,
      height: 1
    }
  });
});

it('should call the dragEnd callback on drop', () => {
  const spy = jest.fn();
  const node = mount(
    <main>
      <div className="DashboardRenderer">
        <div
          className="DashboardObject"
          style={{
            width: '0px',
            height: '0px'
          }}
        >
          <ResizeHandle {...props} onResizeEnd={spy} />
        </div>
      </div>
    </main>
  );

  node
    .find(ResizeHandle)
    .instance()
    .startDragging({
      preventDefault: jest.fn(),
      screenX: 0,
      screenY: 0
    });

  node
    .find(ResizeHandle)
    .instance()
    .saveMouseAndUpdateCardSize({
      screenX: 33,
      screenY: 8
    });

  node
    .find(ResizeHandle)
    .instance()
    .stopDragging();

  expect(spy).toHaveBeenCalled();
});

it('should not update the report when it is dropped somewhere where is no space', () => {
  const spy = jest.fn();
  const node = mount(
    <main>
      <div className="DashboardRenderer">
        <div
          className="DashboardObject"
          style={{
            width: '0px',
            height: '0px'
          }}
        >
          <ResizeHandle
            {...props}
            reports={[
              {
                position: {x: 2, y: 0},
                dimensions: {width: 1, height: 4}
              },
              {
                position: {x: 3, y: 1},
                dimensions: {width: 2, height: 2}
              }
            ]}
          />
        </div>
      </div>
    </main>
  );

  node
    .find(ResizeHandle)
    .instance()
    .startDragging({
      preventDefault: jest.fn(),
      screenX: 0,
      screenY: 0
    });

  node
    .find(ResizeHandle)
    .instance()
    .saveMouseAndUpdateCardSize({
      screenX: 30,
      screenY: 10
    });

  node
    .find(ResizeHandle)
    .instance()
    .stopDragging();

  expect(spy).not.toHaveBeenCalled();
});

it('should create a drop shadow', () => {
  const spy = jest.fn();
  const node = mount(
    <main>
      <div className="DashboardRenderer">
        <div className="DashboardObject">
          <ResizeHandle onDragStart={spy} />
        </div>
      </div>
    </main>
  );

  expect(node.getDOMNode().querySelector('.ResizeHandle__dropShadow')).not.toBeNull();
});

it('should update the dropshadow on drag', () => {
  const node = mount(
    <main>
      <div className="DashboardRenderer">
        <div
          className="DashboardObject"
          style={{
            top: '10px',
            left: '10px'
          }}
        >
          <ResizeHandle {...props} onDragStart={jest.fn()} />
        </div>
      </div>
    </main>
  );

  const spy = jest.spyOn(node.find(ResizeHandle).instance(), 'updateDropPreview');

  node
    .find(ResizeHandle)
    .instance()
    .startDragging({
      preventDefault: jest.fn(),
      screenX: 0,
      screenY: 0
    });

  node
    .find(ResizeHandle)
    .instance()
    .saveMouseAndUpdateCardSize({
      screenX: 5,
      screenY: 5
    });

  expect(spy).toHaveBeenCalled();
});
