import React from 'react';
import {mount} from 'enzyme';

import Tooltip from './Tooltip';
import {addDiagramTooltip} from './service';

jest.mock('./service', () => {
  return {
    addDiagramTooltip: jest.fn()
  };
});

const removeSpy = jest.fn();

const viewer = {
  get: () => {
    return {
      on: (__, fct) => {
        return fct({element: {id: 'elementId'}});
      },
      remove: removeSpy
    };
  }
};
const data = {elementId: 'elementName'};

it('should create a tooltip', () => {
  mount(<Tooltip viewer={viewer} data={data} formatter={v => v} />);

  expect(addDiagramTooltip).toHaveBeenCalledWith(viewer, 'elementId', 'elementName');
});

it('should remove old tooltips on rerendering', () => {
  removeSpy.mockClear();
  mount(
    <Tooltip
      viewer={viewer}
      data={data}
      formatter={v => v}
      hideAbsoluteValue="true"
      hideRelativeValue="true"
    />
  );

  expect(removeSpy).toHaveBeenCalledTimes(1);
});

it('should add a tooltip for every element if tooltips are always shown', () => {
  addDiagramTooltip.mockClear();
  const data = {a: '1', b: '2', c: '3', d: '4', e: '5'};
  mount(<Tooltip viewer={viewer} data={data} formatter={v => v} alwaysShow />);

  expect(addDiagramTooltip).toHaveBeenCalledWith(viewer, 'a', '1');
  expect(addDiagramTooltip).toHaveBeenCalledWith(viewer, 'b', '2');
  expect(addDiagramTooltip).toHaveBeenCalledWith(viewer, 'c', '3');
  expect(addDiagramTooltip).toHaveBeenCalledWith(viewer, 'd', '4');
  expect(addDiagramTooltip).toHaveBeenCalledWith(viewer, 'e', '5');
});
