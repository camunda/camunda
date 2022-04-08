/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import EventsSourceModal from './EventsSourceModal';
import EventsSources from './EventsSources';
import {Dropdown} from 'components';

const props = {
  sources: [
    {type: 'camunda', configuration: {processDefinitionKey: 'src1'}},
    {type: 'external', configuration: {includeAllGroups: true, group: null}},
  ],
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

  expect(props.onChange).toHaveBeenCalledWith(
    [{type: 'external', configuration: {includeAllGroups: true, group: null}}],
    true
  );
});

it('should hide/show source', () => {
  const node = shallow(<EventsSources {...props} />);

  node.find(Dropdown.Option).at(0).simulate('click');

  expect(props.onChange).toHaveBeenCalledWith(
    [
      {hidden: true, type: 'camunda', configuration: {processDefinitionKey: 'src1'}},
      {type: 'external', configuration: {includeAllGroups: true, group: null}},
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
  const node = shallow(
    <EventsSources
      {...props}
      sources={[
        {
          type: 'camunda',
          configuration: {processDefinitionKey: 'src1', eventScope: 'start_end'},
        },
      ]}
    />
  );

  node.find(Dropdown.Option).at(2).simulate('click');

  const modal = node.find('VisibleEventsModal');

  expect(modal.prop('initialScope')).toBe('start_end');

  modal.prop('onConfirm')(['start_end', 'processInstance'], true);
  expect(props.onChange).toHaveBeenCalledWith(
    [
      {
        type: 'camunda',
        configuration: {eventScope: ['start_end', 'processInstance'], processDefinitionKey: 'src1'},
      },
    ],
    true
  );
});
