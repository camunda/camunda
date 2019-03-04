/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import Foldable from './Foldable';
import * as Styled from './styled';

describe('Foldable', () => {
  it('should be folded by default', () => {
    // given
    const mockOnSelection = jest.fn();
    const fooDetailsText = 'Foo Details';
    const fooSummaryText = 'foo summary';
    const node = shallow(
      <Foldable isFoldable={true} isFolded={true}>
        <Foldable.Summary onSelection={mockOnSelection} isSelected={true}>
          {fooSummaryText}
        </Foldable.Summary>
        <Foldable.Details>{fooDetailsText}</Foldable.Details>
      </Foldable>
    );

    // then
    expect(node.state('isFolded')).toBe(true);

    // Summmary
    const SummaryNode = node.find(Foldable.Summary);
    expect(SummaryNode).toHaveLength(1);
    expect(SummaryNode.contains(fooSummaryText)).toBe(true);

    // Summary Label
    const FocusButtonNode = SummaryNode.dive().find(Styled.FocusButton);
    expect(FocusButtonNode).toHaveLength(1);
    expect(FocusButtonNode.prop('onClick')).toBe(mockOnSelection);

    // Summary Expand Button
    const FoldButtonNode = SummaryNode.dive().find(Styled.FoldButton);
    expect(FoldButtonNode).toHaveLength(1);
    expect(FoldButtonNode.prop('onClick')).toBe(node.instance().toggleFold);
    expect(FoldButtonNode.dive().find(Styled.RightIcon)).toHaveLength(1);

    // Details
    const DetailsNode = node.find(Foldable.Details);
    expect(DetailsNode).toHaveLength(1);
    expect(DetailsNode.contains(fooDetailsText)).toBe(true);
    expect(DetailsNode.prop('isFolded')).toBe(true);

    expect(node).toMatchSnapshot();
  });

  it('toggleFold should change folded state', () => {
    // given
    const fooDetailsText = 'Foo Details';
    const fooSummaryText = 'foo summary';
    const node = shallow(
      <Foldable isFoldable={true} isFolded={true}>
        <Foldable.Summary>{fooSummaryText}</Foldable.Summary>
        <Foldable.Details>{fooDetailsText}</Foldable.Details>
      </Foldable>
    );

    // when
    node.instance().toggleFold();
    node.update();

    // then
    expect(node.state('isFolded')).toBe(false);

    // Summary
    const SummaryNode = node.find(Foldable.Summary);
    expect(SummaryNode.dive().find(Styled.DownIcon)).toHaveLength(1);

    // Details
    const DetailsNode = node.find(Foldable.Details);
    expect(DetailsNode.prop('isFolded')).toBe(false);

    expect(node).toMatchSnapshot();
  });
});
