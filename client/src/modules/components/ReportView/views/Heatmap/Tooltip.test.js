import React from 'react';
import {mount} from 'enzyme';

import Tooltip from './Tooltip';
import {addDiagramTooltip} from './service';

jest.mock('./service', () => {return {
  addDiagramTooltip: jest.fn()
}});

const onSpy = jest.fn();
const clearSpy = jest.fn();

const viewer = {
  get: () => { return {
    on:  (__, fct) => {return fct({element: {id: 'elementId'}});},
    clear: clearSpy
  }}
};
const data = { elementId: 'elementName'};

it('should create a tooltip', () => {
  mount(<Tooltip viewer={viewer} data={data} formatter={v=>v} />);

  expect(addDiagramTooltip).toHaveBeenCalledWith(viewer, 'elementId', 'elementName');
});

it('should remove old tooltips on rerendering', () => {
  clearSpy.mockClear();
  mount(<Tooltip viewer={viewer} data={data} formatter={v=>v} />);

  expect(clearSpy).toHaveBeenCalledTimes(1);
});

