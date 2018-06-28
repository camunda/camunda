import React from 'react';
import {shallow} from 'enzyme';

import * as instanceUtils from 'modules/utils/instance/instance';
import {STATE} from 'modules/constants/instance';

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
    instanceUtils.getInstanceState = jest.fn(() => STATE.INCIDENT);
    const node = shallow(
      <StateIcon instance={mockInstance} theme={'dark'} someProp={someProp} />
    );

    // then
    expect(instanceUtils.getInstanceState.mock.calls[0][0]).toBe(mockInstance);
    const IncidentIconNode = node.find(Styled.IncidentIcon);
    expect(IncidentIconNode).toHaveLength(1);
    expect(IncidentIconNode.prop('theme')).toBe('dark');
    expect(IncidentIconNode.prop('someProp')).toBe(someProp);
    expect(node).toMatchSnapshot();
  });

  it('should render Active Icon', () => {
    // given
    const someProp = 'some prop';
    instanceUtils.getInstanceState = jest.fn(() => STATE.ACTIVE);
    const node = shallow(
      <StateIcon instance={mockInstance} theme={'dark'} someProp={someProp} />
    );

    // then
    expect(instanceUtils.getInstanceState.mock.calls[0][0]).toBe(mockInstance);
    const IncidentIconNode = node.find(Styled.ActiveIcon);
    expect(IncidentIconNode).toHaveLength(1);
    expect(IncidentIconNode.prop('theme')).toBe('dark');
    expect(IncidentIconNode.prop('someProp')).toBe(someProp);
    expect(node).toMatchSnapshot();
  });

  it('should render Completed Icon', () => {
    // given
    const someProp = 'some prop';
    instanceUtils.getInstanceState = jest.fn(() => STATE.COMPLETED);
    const node = shallow(
      <StateIcon instance={mockInstance} theme={'dark'} someProp={someProp} />
    );

    // then
    expect(instanceUtils.getInstanceState.mock.calls[0][0]).toBe(mockInstance);
    const IncidentIconNode = node.find(Styled.CompletedIcon);
    expect(IncidentIconNode).toHaveLength(1);
    expect(IncidentIconNode.prop('theme')).toBe('dark');
    expect(IncidentIconNode.prop('someProp')).toBe(someProp);
    expect(node).toMatchSnapshot();
  });

  it('should render Canceled Icon', () => {
    // given
    const someProp = 'some prop';
    instanceUtils.getInstanceState = jest.fn(() => STATE.CANCELED);
    const node = shallow(
      <StateIcon instance={mockInstance} theme={'dark'} someProp={someProp} />
    );

    // then
    expect(instanceUtils.getInstanceState.mock.calls[0][0]).toBe(mockInstance);
    const IncidentIconNode = node.find(Styled.CanceledIcon);
    expect(IncidentIconNode).toHaveLength(1);
    expect(IncidentIconNode.prop('theme')).toBe('dark');
    expect(IncidentIconNode.prop('someProp')).toBe(someProp);
    expect(node).toMatchSnapshot();
  });
});
