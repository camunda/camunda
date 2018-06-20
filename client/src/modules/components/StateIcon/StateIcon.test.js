import React from 'react';
import {shallow} from 'enzyme';

import * as utils from 'modules/utils';

import ThemedStateIcon from './StateIcon';
import * as Styled from './styled';

const {WrappedComponent: StateIcon} = ThemedStateIcon;

describe('StateIcon', () => {
  const mockInstance = {
    state: 'foo',
    incidents: [{key: 'bar'}]
  };

  it('should render Incident Icon', () => {
    // given
    const someProp = 'some prop';
    utils.getInstanceState = jest.fn(() => 'INCIDENT');
    const node = shallow(
      <StateIcon instance={mockInstance} theme={'dark'} someProp={someProp} />
    );

    // then
    expect(utils.getInstanceState.mock.calls[0][0]).toBe(mockInstance);
    const IncidentIconNode = node.find(Styled.IncidentIcon);
    expect(IncidentIconNode).toHaveLength(1);
    expect(IncidentIconNode.prop('theme')).toBe('dark');
    expect(IncidentIconNode.prop('someProp')).toBe(someProp);
    expect(node).toMatchSnapshot();
  });

  it('should render Active Icon', () => {
    // given
    const someProp = 'some prop';
    utils.getInstanceState = jest.fn(() => 'ACTIVE');
    const node = shallow(
      <StateIcon instance={mockInstance} theme={'dark'} someProp={someProp} />
    );

    // then
    expect(utils.getInstanceState.mock.calls[0][0]).toBe(mockInstance);
    const IncidentIconNode = node.find(Styled.ActiveIcon);
    expect(IncidentIconNode).toHaveLength(1);
    expect(IncidentIconNode.prop('theme')).toBe('dark');
    expect(IncidentIconNode.prop('someProp')).toBe(someProp);
    expect(node).toMatchSnapshot();
  });

  it('should render Completed Icon', () => {
    // given
    const someProp = 'some prop';
    utils.getInstanceState = jest.fn(() => 'COMPLETED');
    const node = shallow(
      <StateIcon instance={mockInstance} theme={'dark'} someProp={someProp} />
    );

    // then
    expect(utils.getInstanceState.mock.calls[0][0]).toBe(mockInstance);
    const IncidentIconNode = node.find(Styled.CompletedIcon);
    expect(IncidentIconNode).toHaveLength(1);
    expect(IncidentIconNode.prop('theme')).toBe('dark');
    expect(IncidentIconNode.prop('someProp')).toBe(someProp);
    expect(node).toMatchSnapshot();
  });

  it('should render Cancelled Icon', () => {
    // given
    const someProp = 'some prop';
    utils.getInstanceState = jest.fn(() => 'CANCELLED');
    const node = shallow(
      <StateIcon instance={mockInstance} theme={'dark'} someProp={someProp} />
    );

    // then
    expect(utils.getInstanceState.mock.calls[0][0]).toBe(mockInstance);
    const IncidentIconNode = node.find(Styled.CancelledIcon);
    expect(IncidentIconNode).toHaveLength(1);
    expect(IncidentIconNode.prop('theme')).toBe('dark');
    expect(IncidentIconNode.prop('someProp')).toBe(someProp);
    expect(node).toMatchSnapshot();
  });
});
