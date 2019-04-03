/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import AddButton from './AddButton';

jest.mock('components', () => {
  return {
    Button: props => <button {...props}>{props.children}</button>,
    DashboardObject: ({children}) => <div className="DashboardObject">{children}</div>
  };
});

jest.mock('../service', () => {
  return {
    getOccupiedTiles: jest
      .fn()
      .mockReturnValue({0: {0: true}, 1: {0: true}, 2: {0: true}, 3: {0: true}, 4: {0: true}})
  };
});

jest.mock('./ReportModal', () => () => <p>ReportModal</p>);

const props = {
  tileDimensions: {
    columns: 18
  },
  reports: []
};

it('should open a modal on click', () => {
  const node = mount(<AddButton {...props} />);

  node.find('Button.AddButton').simulate('click');

  expect(node).toIncludeText('ReportModal');
});

it('should call the callback when adding a report', () => {
  const spy = jest.fn();
  const node = mount(<AddButton {...props} addReport={spy} />);

  node.instance().addReport({});

  expect(spy).toHaveBeenCalledWith({
    dimensions: {
      height: 4,
      width: 6
    },
    position: {
      x: 5,
      y: 0
    }
  });
});

it('should place the addButton where is no Report', () => {
  const node = mount(
    <AddButton
      {...props}
      reports={[
        {
          position: {x: 0, y: 0},
          dimensions: {width: 3, height: 1},
          id: '1'
        },
        {
          position: {x: 2, y: 0},
          dimensions: {width: 1, height: 4},
          id: '2'
        },
        {
          position: {x: 3, y: 1},
          dimensions: {width: 2, height: 2},
          id: '3'
        }
      ]}
    />
  );

  const addButtonPosition = node.instance().getAddButtonPosition();

  expect(addButtonPosition.x).toBe(5);
  expect(addButtonPosition.y).toBe(0);
});

it('should render nothing if visible is set to false', () => {
  const node = mount(<AddButton {...props} visible={false} />);

  expect(node.getDOMNode()).toBe(null);
});
