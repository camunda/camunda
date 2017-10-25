import React from 'react';
import { shallow, mount } from 'enzyme';

import Section from './Section';

jest.mock('react-router-dom', () => { return {
  Link: ({children, to}) => {return <a href={to}>{children}</a>},
  withRouter: fn => fn
}});

it('renders without crashing', () => {
  shallow(<Section location={{pathname: '/foo'}} />);
});

it('should contain the provided name', () => {
  const node = mount(<Section name='SectionName' location={{pathname: '/foo'}} />);

  expect(node).toIncludeText('SectionName');
});

it('should contain a link to the provided destination', () => {
  const node = mount(<Section linksTo='/section' location={{pathname: '/foo'}} />);

  expect(node.find('a')).toHaveProp('href', '/section');
})

it('should set the active class if the location pathname includes the active substring', () => {
  const node = mount(<Section active='dashboard' location={{pathname: '/dashboards/1'}} />);

  expect(node.find('.Section')).toHaveClassName('active');
});