import React from 'react';
import {mount} from 'enzyme';

import Header from './Header';

jest.mock('components', () => {
  return {
    Badge: ({children}) => <span>{children}</span>
  };
});

it('should show the count of all instances', () => {
  const node = mount(<Header active="dashboard" instances={123} />);

  expect(node).toIncludeText('123');
});

it('should show other provided properties', () => {
  const node = mount(
    <Header
      active="dashboard"
      instances={123}
      filters={1}
      selections={1}
      incidents={1}
    />
  );

  expect(node).toIncludeText('Filters');
  expect(node).toIncludeText('Selections');
  expect(node).toIncludeText('Incidents');
});

it('should not show the labels if they are not provided', () => {
  const node = mount(
    <Header active="dashboard" instances={123} incidents={1} />
  );

  expect(node).not.toIncludeText('Filters');
  expect(node).not.toIncludeText('Selections');
  expect(node).toIncludeText('Incidents');
});

it('it should show the instances field even if there are no instances', () => {
  const node = mount(<Header active="dashboard" instances={0} />);

  expect(node).toIncludeText('Instances');
});
