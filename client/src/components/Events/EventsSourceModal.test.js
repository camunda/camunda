/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import EventsSourceModalWithErrorHandling from './EventsSourceModal';
import {DefinitionSelection, Button} from 'components';
import {loadVariables} from 'services';

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {...rest, loadVariables: jest.fn().mockReturnValue([])};
});

const EventsSourceModal = EventsSourceModalWithErrorHandling.WrappedComponent;

const testSource = {
  processDefinitionKey: 'foo',
  processDefinitionName: 'Foo',
  versions: ['1'],
  tenants: ['a', 'b'],
  eventScope: 'start_end',
  tracedByBusinessKey: false,
  traceVariable: 'var'
};

const props = {
  initialSource: {},
  existingSources: [],
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data))
};

it('should disable the submit button if no definition selected', () => {
  const node = shallow(<EventsSourceModal {...props} />);

  expect(node.find({variant: 'primary'})).toBeDisabled();
});

it('should disable definition selection in editing mode', () => {
  const node = shallow(<EventsSourceModal {...props} initialSource={testSource} />);

  expect(node.find(DefinitionSelection).props('disabledDefinition')).toBeTruthy();
});

it('load variables after selecting a process definition', () => {
  const node = shallow(<EventsSourceModal {...props} />);

  node.find(DefinitionSelection).prop('onChange')({
    key: 'test',
    name: 'Test',
    versions: ['1'],
    tenantIds: ['a', 'b']
  });

  expect(loadVariables).toHaveBeenCalledWith({
    processDefinitionKey: 'test',
    processDefinitionVersions: ['1'],
    tenantIds: ['a', 'b']
  });
});

it('should apply the source to the state when editing a source', () => {
  const node = shallow(<EventsSourceModal {...props} initialSource={testSource} />);

  expect(node.state().source).toMatchSnapshot();
});

it('should edit a source when clicking confirm', () => {
  const spy = jest.fn();
  const node = shallow(
    <EventsSourceModal
      {...props}
      existingSources={[testSource]}
      initialSource={testSource}
      onConfirm={spy}
    />
  );

  node
    .find({type: 'radio'})
    .at(1)
    .simulate('change');
  node
    .find({type: 'radio'})
    .at(3)
    .simulate('change');

  node.find({variant: 'primary'}).simulate('click');

  expect(spy).toHaveBeenCalledWith([
    {
      eventScope: 'process_instance',
      processDefinitionKey: 'foo',
      processDefinitionName: 'Foo',
      tenants: ['a', 'b'],
      traceVariable: null,
      tracedByBusinessKey: true,
      versions: ['1']
    }
  ]);
});

it('should add a source when clicking confirm', () => {
  const spy = jest.fn();
  const node = shallow(<EventsSourceModal {...props} onConfirm={spy} />);

  node.find(DefinitionSelection).prop('onChange')({
    key: 'test',
    name: 'Test',
    versions: ['1'],
    tenantIds: ['a', 'b']
  });

  node.find('Typeahead').prop('onChange')('boolVar');

  node.find({variant: 'primary'}).simulate('click');

  expect(spy).toHaveBeenCalledWith([
    {
      processDefinitionKey: 'test',
      processDefinitionName: 'Test',
      versions: ['1'],
      tenants: ['a', 'b'],
      tracedByBusinessKey: false,
      traceVariable: 'boolVar',
      eventScope: 'start_end'
    }
  ]);
});

it('should show an error when adding already existing source', async () => {
  const node = await shallow(<EventsSourceModal {...props} existingSources={[testSource]} />);

  node.find(DefinitionSelection).prop('onChange')({
    key: testSource.processDefinitionKey,
    name: testSource.processDefinitionName,
    versions: testSource.versions,
    tenantIds: testSource.tenants
  });

  expect(node.find({error: true})).toExist();
});

it('should add external sources', () => {
  const spy = jest.fn();
  const node = shallow(<EventsSourceModal {...props} onConfirm={spy} />);

  node
    .find('ButtonGroup')
    .find(Button)
    .at(1)
    .simulate('click');

  node.find({variant: 'primary'}).simulate('click');

  expect(spy).toHaveBeenCalledWith([{type: 'external'}]);
});

it('should disable external source if already added', () => {
  const node = shallow(<EventsSourceModal {...props} existingSources={[{type: 'external'}]} />);

  expect(
    node
      .find('ButtonGroup')
      .find(Button)
      .at(1)
  ).toBeDisabled();
});
