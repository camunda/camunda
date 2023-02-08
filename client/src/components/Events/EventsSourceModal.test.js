/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {DefinitionSelection} from 'components';
import {loadVariables} from 'services';

import EventsSourceModalWithErrorHandling from './EventsSourceModal';
import ExternalSource from './ExternalSource';

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {...rest, loadVariables: jest.fn().mockReturnValue([])};
});

jest.mock('request', () => ({post: jest.fn().mockReturnValue({json: jest.fn()})}));

const EventsSourceModal = EventsSourceModalWithErrorHandling.WrappedComponent;

const allExternalGroups = {type: 'external', configuration: {includeAllGroups: true, group: null}};

const testSource = {
  type: 'camunda',
  configuration: {
    processDefinitionKey: 'foo',
    processDefinitionName: 'Foo',
    versions: ['1'],
    tenants: ['a', 'b'],
    eventScope: ['start_end'],
    tracedByBusinessKey: false,
    traceVariable: 'var',
  },
};

const props = {
  initialSource: {},
  existingSources: [],
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

beforeEach(() => {
  loadVariables.mockClear();
});

it('should disable the submit button if no definition selected', () => {
  const node = shallow(<EventsSourceModal {...props} />);

  expect(node.find('[primary]')).toBeDisabled();
});

it('should disable definition selection in editing mode', () => {
  const node = shallow(<EventsSourceModal {...props} initialSource={testSource} />);

  expect(node.find(DefinitionSelection).props('disabledDefinition')).toBeTruthy();
});

it('should hide event type button group in editing', () => {
  const node = shallow(<EventsSourceModal {...props} initialSource={testSource} />);

  expect(node.find('.ButtonGroup')).not.toExist();
});

it('load variables after selecting a process definition', () => {
  const node = shallow(<EventsSourceModal {...props} />);

  node.find(DefinitionSelection).prop('onChange')({
    key: 'test',
    name: 'Test',
    versions: ['1'],
    tenantIds: ['a', 'b'],
  });

  expect(loadVariables).toHaveBeenCalledWith([
    {
      processDefinitionKey: 'test',
      processDefinitionVersions: ['1'],
      tenantIds: ['a', 'b'],
    },
  ]);
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

  node.find({type: 'radio'}).at(1).simulate('change');

  node.find('[primary]').simulate('click');

  expect(spy).toHaveBeenCalledWith(
    [
      {
        type: 'camunda',
        configuration: {
          eventScope: ['start_end'],
          processDefinitionKey: 'foo',
          processDefinitionName: 'Foo',
          tenants: ['a', 'b'],
          traceVariable: null,
          tracedByBusinessKey: true,
          versions: ['1'],
        },
      },
    ],
    true
  );
});

it('should add a source when clicking confirm', () => {
  const spy = jest.fn();
  const node = shallow(<EventsSourceModal {...props} onConfirm={spy} />);

  node.find(DefinitionSelection).prop('onChange')({
    key: 'test',
    name: 'Test',
    versions: ['1'],
    tenantIds: ['a', 'b'],
  });

  node.find('Typeahead').prop('onChange')('boolVar');

  node.find({type: 'radio'}).at(2).simulate('change');

  node.find('[primary]').simulate('click');

  expect(spy).toHaveBeenCalledWith(
    [
      {
        type: 'camunda',
        configuration: {
          processDefinitionKey: 'test',
          processDefinitionName: 'Test',
          versions: ['1'],
          tenants: ['a', 'b'],
          tracedByBusinessKey: false,
          traceVariable: 'boolVar',
          eventScope: ['process_instance'],
        },
      },
    ],
    false
  );
});

it('should show an error when adding already existing source', async () => {
  const node = await shallow(<EventsSourceModal {...props} existingSources={[testSource]} />);

  node.find(DefinitionSelection).prop('onChange')({
    key: testSource.processDefinitionKey,
    name: testSource.processDefinitionName,
    versions: testSource.versions,
    tenantIds: testSource.tenants,
  });

  expect(node.find({error: true})).toExist();
});

it('should add selected external sources', () => {
  const spy = jest.fn();
  const node = shallow(<EventsSourceModal {...props} onConfirm={spy} />);

  node.find('Tabs').prop('onChange')('external');

  const testSource = {type: 'external', configuration: {group: 'testGroup'}};
  node.find(ExternalSource).prop('onChange')([testSource]);

  node.find('[primary]').simulate('click');

  expect(spy).toHaveBeenCalledWith([testSource], false);
});

it('should add new external sources to already existing ones', () => {
  const existingGroupSource = {type: 'external', configuration: {group: 'test'}};
  const spy = jest.fn();
  const node = shallow(
    <EventsSourceModal {...props} onConfirm={spy} existingSources={[existingGroupSource]} />
  );

  node.find('Tabs').prop('onChange')('external');

  const newGroupSource = {type: 'external', configuration: {group: 'testGroup'}};
  node.find(ExternalSource).prop('onChange')([newGroupSource]);

  node.find('[primary]').simulate('click');

  expect(spy).toHaveBeenCalledWith([newGroupSource, existingGroupSource], true);
});

it('should not include existing groups if all external groups is selected', () => {
  const existingGroupSource = {type: 'external', configuration: {group: 'test'}};
  const spy = jest.fn();
  const node = shallow(
    <EventsSourceModal {...props} onConfirm={spy} existingSources={[existingGroupSource]} />
  );

  node.find('Tabs').prop('onChange')('external');
  node.find(ExternalSource).prop('onChange')([allExternalGroups]);

  node.find('[primary]').simulate('click');

  expect(spy).toHaveBeenCalledWith([allExternalGroups], true);
});

it('should add all external groups in auto generation', () => {
  const spy = jest.fn();
  const node = shallow(<EventsSourceModal {...props} onConfirm={spy} autoGenerate={true} />);

  node.find('Tabs').prop('onChange')('external');

  node.find('[primary]').simulate('click');

  expect(node.find(ExternalSource)).not.toExist();
  expect(node.find('.addExternalInfo')).toExist();
  expect(spy).toHaveBeenCalledWith([allExternalGroups], false);
});

it('should contain a change warning and not contain event scope selection', () => {
  const node = shallow(<EventsSourceModal {...props} initialSource={testSource} />);

  expect(node.find('.sourceOptions')).toMatchSnapshot();
});
