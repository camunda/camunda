/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useContext, useEffect} from 'react';
import {mount} from 'enzyme';
import {renderHook, act} from '@testing-library/react-hooks';

import {
  InstanceSelectionProvider,
  InstanceSelectionContext,
  useInstanceSelection
} from './InstanceSelectionContext';

const INSTANCE_IDS = {
  A: '4503599627371065',
  B: '2737113845035996'
};

describe('InstanceSelectionContext', () => {
  it('should return false when there is no selection', () => {
    // when
    const {result} = renderHook(() => useInstanceSelection());

    // then
    expect(result.current.isIdSelected(INSTANCE_IDS.A)).toBe(false);
    expect(result.current.selectedIds).toEqual([]);
  });

  it('should add id to selection and return true', () => {
    // given
    const {result} = renderHook(() => useInstanceSelection());

    // when
    act(() => {
      result.current.handleSelect(INSTANCE_IDS.A)();
    });

    // then
    expect(result.current.isIdSelected(INSTANCE_IDS.A)).toBe(true);
    expect(result.current.isIdSelected(INSTANCE_IDS.B)).toBe(false);
    expect(result.current.selectedIds).toEqual([INSTANCE_IDS.A]);
  });

  it('should add two ids', () => {
    // given
    const {result} = renderHook(() => useInstanceSelection());

    // when
    act(() => {
      result.current.handleSelect(INSTANCE_IDS.A)();
    });

    act(() => {
      result.current.handleSelect(INSTANCE_IDS.B)();
    });

    // then
    expect(result.current.isIdSelected(INSTANCE_IDS.A)).toBe(true);
    expect(result.current.isIdSelected(INSTANCE_IDS.B)).toBe(true);
    expect(result.current.selectedIds).toEqual([
      INSTANCE_IDS.A,
      INSTANCE_IDS.B
    ]);
  });

  it('should add and remove selection and return false', () => {
    // given
    const {result} = renderHook(() => useInstanceSelection());

    // when
    act(() => {
      result.current.handleSelect(INSTANCE_IDS.A)();
    });
    act(() => {
      result.current.handleSelect(INSTANCE_IDS.A)();
    });

    // then
    expect(result.current.isIdSelected(INSTANCE_IDS.A)).toBe(false);
    expect(result.current.selectedIds).toEqual([]);
  });

  it('should be consumable with useContext', () => {
    // given
    const Component = () => {
      const {isIdSelected, handleSelect} = useContext(InstanceSelectionContext);

      useEffect(() => {
        handleSelect(INSTANCE_IDS.A)();
      }, []);

      return <>{isIdSelected(INSTANCE_IDS.A) ? 'selected' : 'not selected'}</>;
    };

    // when
    const node = mount(<Component />, {
      wrappingComponent: InstanceSelectionProvider
    });

    // then
    expect(node.html()).toEqual('selected');
  });
});
