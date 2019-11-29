/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {EntityNameForm} from 'components';

import {updateProcess} from './service';
import ProcessEditWithErrorHandling from './ProcessEdit';
import ProcessRenderer from './ProcessRenderer';
import EventTable from './EventTable';

const ProcessEdit = ProcessEditWithErrorHandling.WrappedComponent;

const props = {
  initialName: 'initial Name',
  initialXml: 'initial XML',
  initialMappings: {},
  id: '1',
  isNew: false,
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data))
};

jest.mock('saveGuard', () => ({
  nowDirty: jest.fn(),
  nowPristine: jest.fn()
}));

jest.mock('./service', () => ({
  updateProcess: jest.fn()
}));

it('should match snapshot', () => {
  const node = shallow(<ProcessEdit {...props} />);

  expect(node).toMatchSnapshot();
});

it('should get the xml from the Process Renderer for process update', async () => {
  const saveSpy = jest.fn();
  const node = shallow(<ProcessEdit {...props} onSave={saveSpy} />);

  const xmlSpy = jest.fn().mockReturnValue('some xml');

  node.find(ProcessRenderer).prop('getXml').action = xmlSpy;
  await node.find(EntityNameForm).prop('onSave')();

  expect(updateProcess).toHaveBeenCalledWith('1', 'initial Name', 'some xml', {});
  expect(xmlSpy).toHaveBeenCalled();
  expect(saveSpy).toHaveBeenCalledWith({
    id: '1',
    name: 'initial Name',
    xml: 'some xml',
    mappings: {}
  });
});

it('should set a new mapping', () => {
  const node = shallow(<ProcessEdit {...props} />);
  node.setState({selectedNode: {id: 'a'}});

  node.find(EventTable).prop('onChange')({eventName: '1'}, true);

  expect(node.find(EventTable).prop('mappings')).toEqual({a: {end: {eventName: '1'}, start: null}});
});

it('should edit a mapping', () => {
  const node = shallow(
    <ProcessEdit {...props} initialMappings={{a: {end: {eventName: '1'}, start: null}}} />
  );
  node.setState({selectedNode: {id: 'a'}});

  node.find(EventTable).prop('onChange')({eventName: '1'}, true, 'start');

  expect(node.find(EventTable).prop('mappings')).toEqual({a: {start: {eventName: '1'}, end: null}});
});

it('should unset a mapping', () => {
  const node = shallow(
    <ProcessEdit {...props} initialMappings={{a: {end: {eventName: '1'}, start: null}}} />
  );
  node.setState({selectedNode: {id: 'a'}});

  node.find(EventTable).prop('onChange')({eventName: '1'}, false);

  expect(node.find(EventTable).prop('mappings')).toEqual({a: {start: null, end: null}});
});

it('should remove mappings when a node is removed', () => {
  const node = shallow(
    <ProcessEdit {...props} initialMappings={{a: {end: {eventName: '1'}, start: null}}} />
  );

  node.find(ProcessRenderer).prop('onChange')({
    get: () => ({get: () => null})
  });

  expect(node.find(EventTable).prop('mappings')).toEqual({});
});
