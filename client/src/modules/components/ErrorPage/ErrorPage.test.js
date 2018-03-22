import React from 'react';
import {mount} from 'enzyme';

import ErrorPage from './ErrorPage';

it('displays the error message passed in props', () => {
  const error = {
    errorMessage: 'error message hello'
  };
  const node = mount(<ErrorPage error={error} />);

  expect(node).toIncludeText('error message hello');
});
