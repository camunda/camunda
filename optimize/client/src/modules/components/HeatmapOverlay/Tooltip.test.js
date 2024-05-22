/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import Tooltip from './Tooltip';
import {addDiagramTooltip} from './service';

jest.useFakeTimers();

jest.mock('./service', () => {
  return {
    addDiagramTooltip: jest.fn(),
  };
});

const removeSpy = jest.fn();

const viewer = {
  get: () => {
    return {
      on: (__, fct) => {
        return fct({element: {id: 'elementId'}});
      },
      remove: removeSpy,
    };
  },
};
const data = {elementId: 'elementName'};

it('should create a tooltip', () => {
  shallow(<Tooltip viewer={viewer} data={data} formatter={(v) => v} />);

  expect(addDiagramTooltip).toHaveBeenCalledWith({
    viewer,
    element: 'elementId',
    tooltipContent: 'elementName',
    theme: undefined,
    onMouseEnter: expect.any(Function),
    onMouseLeave: expect.any(Function),
  });
});

it('should remove old tooltips on rerendering', () => {
  removeSpy.mockClear();
  shallow(<Tooltip viewer={viewer} data={data} formatter={(v) => v} />);

  expect(removeSpy).toHaveBeenCalledTimes(1);
});

it('should pass the theme to addDiagramTooltip if defined', () => {
  shallow(<Tooltip theme="light" viewer={viewer} data={data} formatter={(v) => v} />);

  expect(addDiagramTooltip).toHaveBeenCalledWith({
    viewer,
    element: 'elementId',
    tooltipContent: 'elementName',
    theme: 'light',
    onMouseEnter: expect.any(Function),
    onMouseLeave: expect.any(Function),
  });
});

it('should not add tooltip for every element if hideAbsoluteValue and hideReltiveValue are undefined', () => {
  addDiagramTooltip.mockClear();
  const data = {a: '1', b: '2', c: '3', d: '4', e: '5'};
  shallow(<Tooltip viewer={viewer} data={data} formatter={(v) => v} />);

  expect(addDiagramTooltip).not.toHaveBeenCalledWith({viewer, element: 'a', tooltipContent: '1'});
});

it('should add a tooltip for every element if alwaysShowAbsolute or alwaysShowRelative are true', () => {
  addDiagramTooltip.mockClear();
  const data = {a: '1', b: '2', c: '3', d: '4', e: '5'};
  shallow(<Tooltip theme="light" viewer={viewer} data={data} formatter={(v) => v} alwaysShow />);
  Object.keys(data).forEach((key) => {
    expect(addDiagramTooltip).toHaveBeenCalledWith({
      viewer,
      element: key,
      tooltipContent: data[key],
      theme: 'light',
    });
  });
});

it('should clear tooltip on mouse leave', () => {
  removeSpy.mockClear();
  addDiagramTooltip.mockImplementationOnce(({onMouseLeave}) => {
    onMouseLeave();
  });
  shallow(<Tooltip viewer={viewer} data={data} formatter={(v) => v} />);

  expect(removeSpy).toHaveBeenCalledTimes(2);
});

it('should reset scheduled remove on mouse enter', async () => {
  const viewer = {
    i: 0,
    get: () => {
      return {
        on: (__, fct) => {
          viewer.i++;

          return fct({element: {id: viewer.i === 1 ? 'elementId' : undefined}});
        },
        remove: removeSpy,
      };
    },
  };

  removeSpy.mockClear();
  let mouseEnterFunc;
  addDiagramTooltip.mockImplementationOnce(({onMouseEnter}) => {
    mouseEnterFunc = onMouseEnter;
    return 'elementId';
  });

  const node = shallow(<Tooltip viewer={viewer} data={data} formatter={(v) => v} />);
  await node.update();
  node.instance().componentDidMount();
  mouseEnterFunc();
  jest.runAllTimers();

  expect(removeSpy).not.toHaveBeenCalledWith('elementId');
});
