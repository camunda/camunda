/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

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
  shallow(<Tooltip viewer={viewer} data={data} formatter={v => v} />);

  expect(addDiagramTooltip).toHaveBeenCalledWith(viewer, 'elementId', 'elementName', undefined);
});

it('should remove old tooltips on rerendering', () => {
  removeSpy.mockClear();
  shallow(<Tooltip viewer={viewer} data={data} formatter={v => v} />);

  expect(removeSpy).toHaveBeenCalledTimes(1);
});

it('should pass the theme to addDiagramTooltip if defined', () => {
  shallow(<Tooltip theme="light" viewer={viewer} data={data} formatter={v => v} />);

  expect(addDiagramTooltip).toHaveBeenCalledWith(viewer, 'elementId', 'elementName', 'light');
});

it('should not add tooltip for every element if hideAbsoluteValue and hideReltiveValue are undefined', () => {
  addDiagramTooltip.mockClear();
  const data = {a: '1', b: '2', c: '3', d: '4', e: '5'};
  shallow(<Tooltip viewer={viewer} data={data} formatter={v => v} />);

  expect(addDiagramTooltip).not.toHaveBeenCalledWith(viewer, 'a', '1');
});

it('should add a tooltip for every element if alwaysShowAbsolute or alwaysShowRelative are true', () => {
  addDiagramTooltip.mockClear();
  const data = {a: '1', b: '2', c: '3', d: '4', e: '5'};
  shallow(<Tooltip theme="light" viewer={viewer} data={data} formatter={v => v} alwaysShow />);

  expect(addDiagramTooltip).toHaveBeenCalledWith(viewer, 'a', '1', 'light');
  expect(addDiagramTooltip).toHaveBeenCalledWith(viewer, 'b', '2', 'light');
  expect(addDiagramTooltip).toHaveBeenCalledWith(viewer, 'c', '3', 'light');
  expect(addDiagramTooltip).toHaveBeenCalledWith(viewer, 'd', '4', 'light');
  expect(addDiagramTooltip).toHaveBeenCalledWith(viewer, 'e', '5', 'light');
});
