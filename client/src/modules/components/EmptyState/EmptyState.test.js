/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';

import EmptyState from './EmptyState';

it('should render properly', () => {
  const node = shallow(
    <EmptyState title="some title" description="here is a description" icon="report" />
  );

  expect(node.find('.title')).toHaveText('some title');
  expect(node.find('.description')).toHaveText('here is a description');
  expect(node.find('Icon').prop('type')).toBe('report');
});

it('should render actions', () => {
  const spy = jest.fn();
  const node = shallow(<EmptyState actions={<button onClick={spy}>Click Me</button>} />);

  node.find('button').simulate('click');

  expect(spy).toHaveBeenCalled();
});
