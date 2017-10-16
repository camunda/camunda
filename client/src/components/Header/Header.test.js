import React from 'react';
import { shallow, mount } from 'enzyme';

import Header from './Header';

it('renders without crashing', () => {
  shallow(<Header />);
});

it('includes the name provided as property', () => {
  const name = 'Awesome App';

  const node = shallow(<Header name={name} />);
  expect(node).toIncludeText(name);
});

it('contains the provided children', () => {
  const child = <span>I am a child</span>;
  const node = mount(<Header>{child}</Header>);

  expect(node).toContainReact(child);
});