import React from 'react';
import { shallow } from 'enzyme';

import Footer from './Footer';

it('renders without crashing', () => {
  shallow(<Footer />);
});

it('includes the version number provided as property', () => {
  const version = 'alpha';

  const node = shallow(<Footer version={version} />);
  expect(node).toIncludeText(version);
});

