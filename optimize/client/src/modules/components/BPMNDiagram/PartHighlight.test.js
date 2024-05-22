/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import fs from 'fs';

import React from 'react';
import {mount} from 'enzyme';
import Viewer from 'old-bpmn-js/dist/bpmn-viewer.production.min';

import PartHighlight from './PartHighlight';
import {getDiagramElementsBetween} from 'services';

console.error = jest.fn();

const xml = fs.readFileSync('demo-data/subProcesses.bpmn', {encoding: 'utf-8'});

const loadXml = async (xml) => {
  const viewer = new Viewer();
  await viewer.importXML(xml);
  return viewer;
};

jest.mock('services', () => {
  return {
    getDiagramElementsBetween: jest.fn(),
  };
});

it('should highlight flow nodes', async () => {
  const viewer = await loadXml(xml);
  const canvas = viewer.get('canvas');

  getDiagramElementsBetween.mockReturnValueOnce(['StartEvent_2', 'SubProcess_2']);

  mount(<PartHighlight nodes={[{id: 'a'}, {id: 'b'}]} viewer={viewer} setHasPath={jest.fn()} />);

  expect(canvas.hasMarker('StartEvent_2', 'PartHighlight')).toBe(true);
  expect(canvas.hasMarker('SubProcess_2', 'PartHighlight')).toBe(true);
  expect(canvas.hasMarker('Gateway_2', 'PartHighlight')).toBe(false);
});

it('should clean up after unmounting', async () => {
  const viewer = await loadXml(xml);
  const canvas = viewer.get('canvas');

  getDiagramElementsBetween.mockReturnValueOnce(['StartEvent_2', 'SubProcess_2']);

  const node = mount(
    <PartHighlight nodes={[{id: 'a'}, {id: 'b'}]} viewer={viewer} setHasPath={jest.fn()} />
  );

  node.unmount();

  expect(canvas.hasMarker('StartEvent_2', 'PartHighlight')).toBe(false);
  expect(canvas.hasMarker('SubProcess_2', 'PartHighlight')).toBe(false);
});

it('should pass the info about existing paths to the parent', async () => {
  const viewer = await loadXml(xml);
  const spy = jest.fn();

  getDiagramElementsBetween.mockReturnValueOnce([]);

  mount(<PartHighlight nodes={[{id: 'a'}, {id: 'b'}]} viewer={viewer} setHasPath={spy} />);

  expect(spy).toHaveBeenCalledWith(false);
});
