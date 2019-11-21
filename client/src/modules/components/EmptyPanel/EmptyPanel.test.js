/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';
import EmptyPanel, {WithRowCount} from './EmptyPanel';
import * as Styled from './styled';

const label = 'someLabel';
const SkeletonMock = () => <div data-test="Skeleton"></div>;

describe('EmptyPanel', () => {
  it('should display a warning message', () => {
    const node = mount(<EmptyPanel label={label} type="warning" />);

    expect(node.text()).toContain(label);
    expect(node.find(Styled.WarningIcon)).toExist();
  });

  it('should display a success message', () => {
    const node = mount(<EmptyPanel label={label} type="info" />);
    expect(node.text()).toContain(label);
  });

  it('should display a skeleton', () => {
    const node = mount(
      <EmptyPanel label={label} type="skeleton" Skeleton={SkeletonMock} />
    );

    expect(node.find(SkeletonMock)).toExist();
  });

  describe('WithRowCount', () => {
    it('should calculate number of shown skeleton rows', () => {
      const containerRef = {current: {clientHeight: 200}};

      const node = mount(
        <WithRowCount rowHeight={12} containerRef={containerRef}>
          <SkeletonMock />
        </WithRowCount>
      );

      expect(node.find(SkeletonMock).props().rowsToDisplay).toBe(22);
    });
  });
});
