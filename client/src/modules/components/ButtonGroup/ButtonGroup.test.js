import React from 'react';
import { mount } from 'enzyme';

import ButtonGroup from './ButtonGroup';

jest.mock('components', () => {return {
  Button: ({children}) => <button>{children}</button>
}});

it('should render without crashing', () => {
  mount(<ButtonGroup />);
});
