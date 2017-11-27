import React from 'react';
import { shallow, mount } from 'enzyme';

import HeaderNavItem from './HeaderNavItem';

jest.mock('react-router-dom', () => { return {
  Link: ({children, to}) => {return <a href={to}>{children}</a>},
  withRouter: fn => fn
}});

it('renders without crashing', () => {
  shallow(<HeaderNavItem location={{pathname: '/foo'}} />);
});

it('should contain the provided name', () => {
  const node = mount(<HeaderNavItem name='SectionName' location={{pathname: '/foo'}} />);

  expect(node).toIncludeText('SectionName');
});

it('should contain a link to the provided destination', () => {
  const node = mount(<HeaderNavItem linksTo='/section' location={{pathname: '/foo'}} />);

  expect(node.find('a')).toHaveProp('href', '/section');
})

it('should set the active class if the location pathname includes the active substring', () => {
  const node = mount(<HeaderNavItem active='dashboard' location={{pathname: '/dashboards/1'}} />);

  expect(node.find('.HeaderNav__item')).toHaveClassName('active');
});
