import React from 'react';
import {shallow} from 'enzyme';

import * as instanceUtils from 'modules/utils/instance/instance';
import {STATE} from 'modules/constants';

import ThemedStateIcon from './StateIcon';
import * as Styled from './styled';

const {WrappedComponent: StateIcon} = ThemedStateIcon;

describe('StateIcon', () => {
  it('should render Incident Icon', () => {
    // given
    const someProp = 'some prop';
    instanceUtils.getInstanceState = jest.fn(() => STATE.INCIDENT);
    const node = shallow(
      <StateIcon state={STATE.INCIDENT} theme={'dark'} someProp={someProp} />
    );

    // then
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
      <StateIcon state={STATE.ACTIVE} theme={'dark'} someProp={someProp} />
    );

    // then
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
      <StateIcon state={STATE.COMPLETED} theme={'dark'} someProp={someProp} />
    );

    // then
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
      <StateIcon state={STATE.CANCELED} theme={'dark'} someProp={someProp} />
    );

    // then
    const IncidentIconNode = node.find(Styled.CanceledIcon);
    expect(IncidentIconNode).toHaveLength(1);
    expect(IncidentIconNode.prop('theme')).toBe('dark');
    expect(IncidentIconNode.prop('someProp')).toBe(someProp);
    expect(node).toMatchSnapshot();
  });

  it('should render an Alias Icon if no suitable icon is available', () => {
    // given
    const someProp = 'some prop';
    console.error = jest.fn();
    instanceUtils.getInstanceState = jest.fn(() => 'SomeUnknownState');
    const node = shallow(
      <StateIcon
        state={'SomeUnknownState'}
        theme={'dark'}
        someProp={someProp}
      />
    );

    // then
    const AliasIconNode = node.find(Styled.AliasIcon);
    expect(AliasIconNode).toHaveLength(1);
    expect(AliasIconNode.prop('theme')).toBe('dark');
    expect(AliasIconNode.prop('someProp')).toBe(someProp);
    expect(node).toMatchSnapshot();
  });
});
