/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {FlowNodeTimeStampProvider} from 'modules/contexts/FlowNodeTimeStampContext';

import Pill from 'modules/components/Pill';
import {PILL_TYPE} from 'modules/constants';

import TimeStampPill from './TimeStampPill';
import {createMockDataManager} from 'modules/testHelpers/dataManager';
import {DataManagerProvider} from 'modules/DataManager';

jest.mock('modules/utils/bpmn');

const renderNode = node => {
  return mount(
    <ThemeProvider>
      <DataManagerProvider>
        <FlowNodeTimeStampProvider>{node}</FlowNodeTimeStampProvider>
      </DataManagerProvider>
    </ThemeProvider>
  );
};

describe('TimeStampPill', () => {
  let node;

  beforeEach(() => {
    createMockDataManager();
    node = renderNode(<TimeStampPill />);
  });

  it('should pass context props ', () => {
    const ComponentNode = node.find('TimeStampPill');
    expect(node.find('TimeStampPill')).toExist();
    expect(ComponentNode.prop('showTimeStamp')).toBe(false);
    expect(ComponentNode.prop('onTimeStampToggle')).toBe(
      node.find(FlowNodeTimeStampProvider).instance().handleTimeStampToggle
    );
  });

  it('should render a pill element with a type property', () => {
    const PillNode = node.find(Pill);
    expect(PillNode.prop('type')).toBe(PILL_TYPE.TIMESTAMP);
  });

  describe('Label', () => {
    it('should render "Show" label when timestamps are hidden', () => {
      //given
      const PillNode = node.find('TimeStampPill');

      //when
      expect(PillNode.prop('showTimeStamp')).toBe(false);

      //then
      expect(PillNode.text()).toContain('Show End Time');
    });

    it('should render "Hide" label, when timestamps are visible', () => {
      //given
      node.find(FlowNodeTimeStampProvider).setState({showTimeStamp: true});
      const PillNode = node.find('TimeStampPill');

      //when
      expect(PillNode.prop('showTimeStamp')).toBe(true);

      //then
      expect(PillNode.text()).toContain('Hide End Time');
    });
  });
});
