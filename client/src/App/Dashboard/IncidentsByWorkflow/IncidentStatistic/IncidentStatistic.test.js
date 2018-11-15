import React from 'react';
import {shallow} from 'enzyme';
import IncidentStatistic from './IncidentStatistic';
import * as Styled from './styled';

describe('IncidentStatistic', () => {
  it('should display the right data', () => {
    const node = shallow(
      <IncidentStatistic
        incidentsCount={10}
        label="someLabel"
        activeCount={8}
      />
    );

    expect(node.find(Styled.IncidentsCount).text()).toBe('10');
    expect(node.find(Styled.Label).text()).toBe('someLabel');
    expect(node.find(Styled.ActiveCount).text()).toBe('8');
    expect(node.find(Styled.IncidentsBar)).toExist();
    expect(node).toMatchSnapshot();
  });
});
