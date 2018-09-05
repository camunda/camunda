import React from 'react';
import {mount} from 'enzyme';

import Dashboards from './Dashboards';

jest.mock('components', () => {
  return {EntityList: props => <span id="EntityList">{JSON.stringify(props)}</span>};
});

describe('getReportCountLabel', () => {
  let node = {};
  beforeEach(async () => {
    node = await mount(<Dashboards />);
  });
  it('should return empty label if there are no reports', () => {
    const label = node.instance().getReportCountLabel({reports: []});
    expect(label).toBe('0 Reports');
  });

  it('should return correct single report label', () => {
    const label = node.instance().getReportCountLabel({reports: [{}]});
    expect(label).toBe('1 Report');
  });

  it('should return correct multiple report label', () => {
    const label = node.instance().getReportCountLabel({reports: [{}, {}]});
    expect(label).toBe('2 Reports');
  });
});
