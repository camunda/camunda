/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import App from './App';

jest.mock('notifications', () => ({addNotification: jest.fn(), Notifications: () => <span />}));

jest.mock('translation', () => ({
  initTranslation: jest.fn(),
}));

jest.mock('hooks', () => ({
  useErrorHandling: () => ({
    mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  }),
  useUser: jest.fn(),
}));

it('should show an error message when it is not possible to initilize the translation', async () => {
  const node = shallow(<App error="test error message" />);

  expect(node).toMatchSnapshot();
});

it('should render the last component in the url', () => {
  const node = shallow(<App />);

  runAllEffects();

  const component = node
    .find({
      path: '/(report|dashboard/instant|dashboard|collection|events/processes|processes/report)/*',
    })
    .prop('render')({
    location: {pathname: '/collection/cid/dashboard/did/report/rid'},
  });
  const renderedEntity = shallow(component);

  expect(renderedEntity.dive().name()).toBe('Report');
  expect(renderedEntity.props().match.params.id).toBe('rid');
});

it('should render dashboar for dashboard/instant route', () => {
  const node = shallow(<App />);

  runAllEffects();

  const component = node
    .find({
      path: '/(report|dashboard/instant|dashboard|collection|events/processes|processes/report)/*',
    })
    .prop('render')({
    location: {pathname: '/dashboard/instant/defKey'},
  });
  const renderedEntity = shallow(component);

  expect(renderedEntity.dive().name()).toBe('Dashboard');
  expect(renderedEntity.props().match.params.id).toBe('defKey');
});
