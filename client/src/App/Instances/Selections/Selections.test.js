import React from 'react';
import {mount} from 'enzyme';

import {ThemeProvider} from 'modules/theme';
import {SelectionProvider} from 'modules/contexts/SelectionContext';
import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';
import CollapsablePanel from 'modules/components/CollapsablePanel';
import ComboBadge from 'modules/components/ComboBadge';

import Selections from './Selections';

describe('Selections', () => {
  it('should render properly', () => {
    // given
    const selectionCount = 1;
    const instancesInSelectionsCount = 2;
    const node = mount(
      <ThemeProvider>
        <CollapsablePanelProvider>
          <SelectionProvider getFilterQuery={jest.fn()}>
            <Selections />
          </SelectionProvider>
        </CollapsablePanelProvider>
      </ThemeProvider>
    );
    node
      .find('BasicSelectionProvider')
      .setState({selectionCount, instancesInSelectionsCount});

    // then
    const header = node.find(CollapsablePanel.Header);
    expect(header.contains('Selections')).toBe(true);

    // badge
    const leftBadge = header
      .find(ComboBadge.Left)
      .find('div[data-test="badge"]');
    const rightBadge = header
      .find(ComboBadge.Right)
      .find('div[data-test="badge"]');
    expect(leftBadge.text()).toEqual(`${selectionCount}`);
    expect(rightBadge.text()).toEqual(`${instancesInSelectionsCount}`);

    // SelectionList
    const body = node.find(CollapsablePanel.Body);
    expect(body.find('SelectionList')).toHaveLength(1);
  });
});
