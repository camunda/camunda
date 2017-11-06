import React from 'react';
import {mount} from 'enzyme';

import Dashboards from './Dashboards';

import {load} from '../EntityList/service';

jest.mock('../EntityList/service', () => {
  return {
    load: jest.fn()
  }
});

jest.mock('react-router-dom', () => {
  return {
    Redirect: ({to}) => {
      return <div>REDIRECT to {to}</div>
    },
    Link: ({children, to, onClick, id}) => {
      return <a id={id} href={to} onClick={onClick}>{children}</a>
    }
  }
});

const props = {};

const sampleReports = [
  {
    name: 'name 1',
    lastModifier: 'lastModifier 1',
    lastModified: '2017-11-11T11:11:11.1111+0200',
  },
  {
    name: 'name 2',
    lastModifier: 'lastModifier 2',
    lastModified: '2017-11-11T11:12:11.1111+0200',
  }
];

load.mockReturnValue(sampleReports);

it('should sort dashboards by last modified property', async () => {
  const node = mount(Dashboards());

  //this will make method to be invoked twice, but we can wait on second call
  await node.instance().loadEntities();
  expect(node.state().data[0]).toEqual(sampleReports[1]);
});
