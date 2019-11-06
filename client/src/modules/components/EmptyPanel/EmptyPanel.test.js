/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';
import EmptyPanel, {WithRowCount} from './EmptyPanel';

describe('EmptyPanel', () => {
  it('should display a warning message', () => {
    const node = mount(<EmptyPanel label="someLabel" type="warning" />);
    expect(node).toMatchSnapshot();
  });

  it('should display a success message', () => {
    const node = mount(<EmptyPanel label="someLabel" type="info" />);
    expect(node).toMatchSnapshot();
  });

  it('should display a skeleton', () => {
    const node = mount(
      <EmptyPanel label="someLabel" type="info" Skeleton={<div></div>} />
    );
    expect(node).toMatchSnapshot();
  });

  describe('WithRowCount', () => {
    it('should calculate number of shown skeleton rows', () => {
      const containerRef = {current: {clientHeight: 200}};
      const SkeletonMock = () => <div />;

      const node = mount(
        <WithRowCount rowHeight={12} containerRef={containerRef}>
          <SkeletonMock />
        </WithRowCount>
      );

      expect(node.find(SkeletonMock).props().rowsToDisplay).toBe(15);
    });
  });
});
