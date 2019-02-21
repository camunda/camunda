import React from 'react';
import {shallow} from 'enzyme';

import Header from './Header';
jest.mock('react-router-dom', () => {
  return {
    Link: ({children, to}) => {
      return <a href={to}>{children}</a>;
    },
    withRouter: fn => fn
  };
});

it('matches the snapshot', () => {
  const node = shallow(<Header name="Awesome App" location={{pathname: '/'}} />);

  expect(node).toMatchSnapshot();
});
