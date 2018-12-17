import React from 'react';
import {mount} from 'enzyme';

import {ThemeProvider} from 'modules/theme';
import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';
import {SelectionProvider} from 'modules/contexts/SelectionContext';

import Selections from './Selections';

jest.mock(
  './SelectionList',
  () =>
    function SelectionList(props) {
      return <div {...props}>Selection List</div>;
    }
);

// UI:
// [] It's an expandable
// [] There is a header "Selections"
// [] There is a badge which has both selectionCount and instancesInSelectionCount
// [] The body renders SelectionList
describe('Selections', () => {
  it('should render properly', () => {
    // given
    const node = mount(
      <ThemeProvider>
        <CollapsablePanelProvider>
          <SelectionProvider getFilterQuery={jest.fn()}>
            <Selections />
          </SelectionProvider>
        </CollapsablePanelProvider>
      </ThemeProvider>
    );

    console.log(node.debug());
  });
});
