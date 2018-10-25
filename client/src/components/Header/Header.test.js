import React from 'react';
import {shallow} from 'enzyme';

import Header from './Header';

it('matches the snapshot', () => {
  const node = shallow(<Header name="Awesome App" />);

  expect(node).toMatchSnapshot();
});
