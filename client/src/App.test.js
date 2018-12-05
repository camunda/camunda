import React from 'react';
import {shallow} from 'enzyme';

import App from './App';

it('should include a header for the Home page', () => {
  const node = shallow(<App />);
  const content = shallow(node.find('Route').prop('render')({location: {pathname: '/'}}));

  expect(content.find('Header')).toBePresent();
  expect(content.find('Footer')).toBePresent();
});

it('should not include a header for the login page', () => {
  const node = shallow(<App />);
  const content = shallow(node.find('Route').prop('render')({location: {pathname: '/login'}}));

  expect(content.find('Header')).not.toBePresent();
  expect(content.find('Footer')).not.toBePresent();
});

it('should not include a header for shared resources', () => {
  const node = shallow(<App />);
  const content = shallow(
    node.find('Route').prop('render')({location: {pathname: '/share/report/3'}})
  );

  expect(content.find('Header')).not.toBePresent();
  expect(content.find('Footer')).not.toBePresent();
});
