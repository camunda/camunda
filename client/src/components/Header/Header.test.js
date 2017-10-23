import React from 'react';
import { shallow, mount } from 'enzyme';

import Header from './Header';

jest.mock('credentials', () => {return {
  getToken: jest.fn(),
}});
jest.mock('react-router-dom', () => { return {
  Link: ({children}) => {return <a>{children}</a>}
}});
jest.mock('./LogoutButton', () => {return <div>logout</div>});
jest.mock('./Section', () => {return <div>Section</div>});

it('renders without crashing', () => {
  shallow(<Header />);
});

it('includes the name provided as property', () => {
  const name = 'Awesome App';

  const node = mount(<Header name={name} />);
  expect(node).toIncludeText(name);
});
