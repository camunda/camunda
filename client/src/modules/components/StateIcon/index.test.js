/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {STATE} from 'modules/constants';

import ThemedStateIcon from './index';
import * as Styled from './styled';

const {WrappedComponent: StateIcon} = ThemedStateIcon;

describe('StateIcon', () => {
  it('should render Incident Icon', () => {
    // given
    const someProp = 'some prop';
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
