import React from 'react';
import { shallow, mount } from 'enzyme';

import Header from './Header';

jest.mock('react-router-dom', () => { return {
  Link: ({children}) => {return <a>{children}</a>}
}});

it('renders without crashing', () => {
  shallow(<Header />);
});

it('includes the name provided as property', () => {
  const name = 'Awesome App';

  const node = mount(<Header name={name} />);
  expect(node).toIncludeText(name);
});

it('contains the provided children', () => {
  const child = <span>I am a child</span>;
  const node = mount(<Header>{child}</Header>);

  expect(node).toContainReact(child);
});