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
    const ExpandButtonNode = SummaryNode.dive().find(Styled.ExpandButton);
    expect(ExpandButtonNode).toHaveLength(1);
    expect(ExpandButtonNode.prop('onClick')).toBe(node.instance().toggleFold);

    // Details
    const DetailsNode = node.find(Foldable.Details);
    expect(DetailsNode).toHaveLength(1);
    expect(DetailsNode.contains(fooDetailsText)).toBe(true);
    expect(DetailsNode.prop('isFolded')).toBe(true);

    expect(node).toMatchSnapshot();
  });

  it('should not show the folding button when "isFoldable" prop is set "false"', () => {
    // given
    const mockOnSelection = jest.fn();
    const fooDetailsText = 'Foo Details';
    const fooSummaryText = 'foo summary';
    const node = shallow(
      <Foldable isFoldable={false} isFolded={true}>
        <Foldable.Summary onSelection={mockOnSelection} isSelected={true}>
          {fooSummaryText}
        </Foldable.Summary>
        <Foldable.Details>{fooDetailsText}</Foldable.Details>
      </Foldable>
    );

    // then
    const ExpandButtonNode = node
      .find(Foldable.Summary)
      .dive()
      .find(Styled.ExpandButton);

    expect(ExpandButtonNode).toHaveLength(0);
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
    expect(SummaryNode.dive().find(Styled.ExpandButton)).toHaveLength(1);

    // Details
    const DetailsNode = node.find(Foldable.Details);
    expect(DetailsNode.prop('isFolded')).toBe(false);

    expect(node).toMatchSnapshot();
  });
});
