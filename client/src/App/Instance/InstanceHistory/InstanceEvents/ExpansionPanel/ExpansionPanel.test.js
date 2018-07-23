import React from 'react';
import {shallow} from 'enzyme';

import ExpansionPanel from './ExpansionPanel';
import * as Styled from './styled';

describe('ExpansionPanel', () => {
  it('should not be expanded by default', () => {
    // given
    const fooDetailsText = 'Foo Details';
    const fooSummaryText = 'foo summary';
    const node = shallow(
      <ExpansionPanel>
        <ExpansionPanel.Summary>{fooSummaryText}</ExpansionPanel.Summary>
        <ExpansionPanel.Details>{fooDetailsText}</ExpansionPanel.Details>
      </ExpansionPanel>
    );

    // then
    expect(node.state('expanded')).toBe(false);

    // Summmary
    const SummaryNode = node.find(ExpansionPanel.Summary);
    expect(SummaryNode).toHaveLength(1);
    expect(SummaryNode.contains(fooSummaryText)).toBe(true);

    // Summary Expand Button
    const ExpandButtonNode = SummaryNode.dive().find(Styled.ExpandButton);
    expect(ExpandButtonNode).toHaveLength(1);
    expect(ExpandButtonNode.prop('onClick')).toBe(node.instance().toggleExpand);
    expect(ExpandButtonNode.dive().find(Styled.RightIcon)).toHaveLength(1);

    // Details
    const DetailsNode = node.find(ExpansionPanel.Details);
    expect(DetailsNode).toHaveLength(1);
    expect(DetailsNode.contains(fooDetailsText)).toBe(true);
    expect(DetailsNode.prop('expanded')).toBe(false);

    expect(node).toMatchSnapshot();
  });

  it('toggleExpand should change expanded state', () => {
    // given
    const fooDetailsText = 'Foo Details';
    const fooSummaryText = 'foo summary';
    const node = shallow(
      <ExpansionPanel>
        <ExpansionPanel.Summary>{fooSummaryText}</ExpansionPanel.Summary>
        <ExpansionPanel.Details>{fooDetailsText}</ExpansionPanel.Details>
      </ExpansionPanel>
    );

    // when
    node.instance().toggleExpand();
    node.update();

    // then
    expect(node.state('expanded')).toBe(true);

    // Summary
    const SummaryNode = node.find(ExpansionPanel.Summary);
    expect(SummaryNode.dive().find(Styled.DownIcon)).toHaveLength(1);

    // Details
    const DetailsNode = node.find(ExpansionPanel.Details);
    expect(DetailsNode.prop('expanded')).toBe(true);

    expect(node).toMatchSnapshot();
  });
});
