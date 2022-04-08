/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import fs from 'fs';

import Viewer from 'bpmn-js/dist/bpmn-viewer.production.min';

import {getDiagramElementsBetween} from './diagramServices';

console.error = jest.fn();
const xml = fs.readFileSync('demo-data/subProcesses.bpmn', {encoding: 'utf-8'});

const loadXml = async (xml) => {
  const viewer = new Viewer();
  await viewer.importXML(xml);
  return viewer;
};

it('should correctly calculate flow nodes between two nodes', async () => {
  const viewer = await loadXml(xml);
  const registry = viewer.get('elementRegistry');

  const start = registry.get('StartEvent_2').businessObject;
  const end = registry.get('EndEvent_2_1').businessObject;

  const nodes = getDiagramElementsBetween(start, end, viewer);

  expect(nodes).toContain('StartEvent_2');
  expect(nodes).toContain('SubProcess_2');
  expect(nodes).toContain('Gateway_2');
  expect(nodes).toContain('EndEvent_2_1');
  expect(nodes).not.toContain('SubProcess_4');
});

it('should work across subprocess boundaries', async () => {
  const viewer = await loadXml(xml);
  const registry = viewer.get('elementRegistry');

  const start = registry.get('Task_3').businessObject;
  const end = registry.get('Task_4').businessObject;

  const nodes = getDiagramElementsBetween(start, end, viewer);

  expect(nodes).toContain('EndEvent_3');
  expect(nodes).toContain('StartEvent_4');
  expect(nodes).toContain('Gateway_2');
  expect(nodes).not.toContain('StartEvent_3');
});

it('should work with boundary events', async () => {
  const viewer = await loadXml(xml);
  const registry = viewer.get('elementRegistry');

  const start = registry.get('Task_3').businessObject;
  const end = registry.get('Task_5').businessObject;

  const nodes = getDiagramElementsBetween(start, end, viewer);

  expect(nodes).toContain('BoundaryEvent');
});

it('should highlight nodes inside subprocesses', async () => {
  const viewer = await loadXml(xml);
  const registry = viewer.get('elementRegistry');

  const start = registry.get('StartEvent_2').businessObject;
  const end = registry.get('Gateway_2').businessObject;

  const nodes = getDiagramElementsBetween(start, end, viewer);

  expect(nodes).toContain('StartEvent_3');
  expect(nodes).toContain('Task_3');
  expect(nodes).toContain('EndEvent_3');
});

it('should not highlight nodes in different subprocesses if no top level path exists', async () => {
  const viewer = await loadXml(xml);
  const registry = viewer.get('elementRegistry');

  const start = registry.get('Task_4').businessObject;
  const end = registry.get('Task_3').businessObject;

  const nodes = getDiagramElementsBetween(start, end, viewer);

  expect(nodes).not.toContain('EndEvent_4');
  expect(nodes).not.toContain('StartEvent_3');
  expect(nodes).not.toContain('Task_4');
  expect(nodes).not.toContain('Task_3');
});

it('should work correctly if a boundary event is set as end node', async () => {
  const viewer = await loadXml(xml);
  const registry = viewer.get('elementRegistry');

  const start = registry.get('StartEvent_2').businessObject;
  const end = registry.get('BoundaryEvent').businessObject;

  const nodes = getDiagramElementsBetween(start, end, viewer);

  expect(nodes).toContain('StartEvent_2');
  expect(nodes).toContain('SubProcess_2');
  expect(nodes).toContain('BoundaryEvent');
});
