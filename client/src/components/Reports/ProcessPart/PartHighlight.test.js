import fs from 'fs';

import React from 'react';
import Viewer from 'bpmn-js/dist/bpmn-viewer.production.min';

import PartHighlight from './PartHighlight';

import {mount} from 'enzyme';

console.error = jest.fn();

const xml = fs.readFileSync(__dirname + '/PartHighlight.test.xml', {encoding: 'utf-8'});

const loadXml = async xml =>
  new Promise(resolve => {
    const viewer = new Viewer();
    viewer.importXML(xml, () => resolve(viewer));
  });

it('should correctly calculate flow nodes between two selected nodes', async () => {
  const viewer = await loadXml(xml);
  const registry = viewer.get('elementRegistry');
  const canvas = viewer.get('canvas');

  const start = registry.get('approveInvoice').businessObject;
  const end = registry.get('prepareBankTransfer').businessObject;

  mount(<PartHighlight nodes={[start, end]} viewer={viewer} />);

  expect(canvas.hasMarker('approveInvoice', 'PartHighlight')).toBe(true);
  expect(canvas.hasMarker('prepareBankTransfer', 'PartHighlight')).toBe(true);
  expect(canvas.hasMarker('reviewInvoice', 'PartHighlight')).toBe(true);
  expect(canvas.hasMarker('ServiceTask_1', 'PartHighlight')).toBe(false);
});
