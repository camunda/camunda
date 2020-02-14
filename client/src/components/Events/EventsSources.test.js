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
  sources: [{type: 'camunda', processDefinitionKey: 'src1'}, {type: 'external'}]
};

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
  const spy = jest.fn();
  const node = shallow(<EventsSources {...props} onChange={spy} />);

  node
    .find(Dropdown.Option)
    .at(1)
    .simulate('click');

  node.find('DeleterErrorHandler').prop('deleteEntity')(props.sources[0]);

  expect(spy).toHaveBeenCalledWith([{type: 'external'}]);
});

it('should edit a source from the list', () => {
  const node = shallow(<EventsSources {...props} />);

  node
    .find(Dropdown.Option)
    .at(0)
    .simulate('click');

  expect(node.find(EventsSourceModal).prop('initialSource')).toEqual({
    processDefinitionKey: 'src1',
    type: 'camunda'
  });
});
