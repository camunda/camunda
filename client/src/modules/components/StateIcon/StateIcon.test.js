import React from 'react';
import {shallow} from 'enzyme';

import * as utils from 'modules/utils/utils';

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
    utils.getInstanceState = jest.fn(() => utils.INSTANCE_STATE.INCIDENT);
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
    utils.getInstanceState = jest.fn(() => utils.INSTANCE_STATE.ACTIVE);
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
    utils.getInstanceState = jest.fn(() => utils.INSTANCE_STATE.COMPLETED);
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

  it('should render CANCELED Icon', () => {
    // given
    const someProp = 'some prop';
    utils.getInstanceState = jest.fn(() => utils.INSTANCE_STATE.CANCELED);
    const node = shallow(
      <StateIcon instance={mockInstance} theme={'dark'} someProp={someProp} />
    );

    // then
    expect(utils.getInstanceState.mock.calls[0][0]).toBe(mockInstance);
    const IncidentIconNode = node.find(Styled.CANCELEDIcon);
    expect(IncidentIconNode).toHaveLength(1);
    expect(IncidentIconNode.prop('theme')).toBe('dark');
    expect(IncidentIconNode.prop('someProp')).toBe(someProp);
    expect(node).toMatchSnapshot();
  });
});
