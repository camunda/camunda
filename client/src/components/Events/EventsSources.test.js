/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import EventsSourceModal from './EventsSourceModal';
import EventsSources from './EventsSources';
import {Dropdown} from 'components';

const props = {
  sources: [{type: 'camunda', configuration: {processDefinitionKey: 'src1'}}, {type: 'external'}],
  onChange: jest.fn(),
};

beforeEach(() => {
  props.onChange.mockClear();
});

it('should match snapshot', () => {
  const node = shallow(<EventsSources {...props} />);

  expect(node).toMatchSnapshot();
});

it('should open addSourceModal when clicking the add button', () => {
  const node = shallow(<EventsSources {...props} />);

  node.find('.addProcess').simulate('click');

  expect(node.find(EventsSourceModal)).toExist();
});

it('should remove a source from the list', () => {
  const node = shallow(<EventsSources {...props} />);

  node.find(Dropdown.Option).at(2).simulate('click');

  node.find('DeleterErrorHandler').prop('deleteEntity')(props.sources[0]);

  expect(props.onChange).toHaveBeenCalledWith([{type: 'external'}], true);
});

it('should hide/show source', () => {
  const node = shallow(<EventsSources {...props} />);

  node.find(Dropdown.Option).at(0).simulate('click');

  expect(props.onChange).toHaveBeenCalledWith(
    [
      {hidden: true, type: 'camunda', configuration: {processDefinitionKey: 'src1'}},
      {type: 'external'},
    ],
    false
  );
});

it('should edit a source from the list', () => {
  const node = shallow(<EventsSources {...props} />);

  node.find(Dropdown.Option).at(1).simulate('click');

  expect(node.find(EventsSourceModal).prop('initialSource')).toEqual({
    configuration: {processDefinitionKey: 'src1'},
    type: 'camunda',
  });
});

it('should edit a scope of a source', () => {
  const node = shallow(<EventsSources {...props} />);

  node.find(Dropdown.Option).at(2).simulate('click');

  const modal = node.find('VisibleEventsModal');
  expect(modal).toExist();

  modal.prop('onConfirm')(['start_end', 'processInstance']);

  expect(props.onChange).toHaveBeenCalledWith(
    [
      {
        type: 'camunda',
        configuration: {eventScope: ['start_end', 'processInstance'], processDefinitionKey: 'src1'},
      },
      {type: 'external'},
    ],
    true
  );
});
