import React from 'react';
import {shallow} from 'enzyme';
import EmptyIncidents from './EmptyIncidents';

describe('EmptyIncidents', () => {
  it('should display a warning message', () => {
    const node = shallow(<EmptyIncidents label="someLabel" type="warning" />);
    expect(node).toMatchSnapshot();
  });

  it('should display a success message', () => {
    const node = shallow(<EmptyIncidents label="someLabel" type="success" />);
    expect(node).toMatchSnapshot();
  });
});
