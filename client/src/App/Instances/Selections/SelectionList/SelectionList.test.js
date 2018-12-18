import React from 'react';
import {mount} from 'enzyme';

import {createSelection, mockResolvedAsyncFn} from 'modules/testUtils';
import {ThemeProvider} from 'modules/theme';
import {SelectionProvider} from 'modules/contexts/SelectionContext';
import * as instancesApi from 'modules/api/instances/instances';
import {MESSAGES_TYPE, OPERATION_TYPE} from 'modules/constants';
import {MESSAGES} from 'modules/components/ContextualMessage/constants';

import SelectionList from './SelectionList';
import {NO_SELECTIONS_MESSAGE} from './constants';

instancesApi.applyOperation = mockResolvedAsyncFn();

describe('SelectionList', () => {
  let node;
  beforeEach(async () => {
    node = mount(
      <ThemeProvider>
        <SelectionProvider getFilterQuery={jest.fn()}>
          <SelectionList />
        </SelectionProvider>
      </ThemeProvider>
    );
  });

  it('should render an empty list of selections', () => {
    const liNodes = node.find('li[data-test="selection-list-item"]');

    // 2 list nodes
    expect(liNodes).toHaveLength(0);

    // empty message
    expect(
      node.find('div[data-test="empty-selection-list-message"]').text()
    ).toEqual(NO_SELECTIONS_MESSAGE);
  });

  it('should render list of selections', () => {
    // given
    const selections = [
      createSelection({
        selectionId: 1
      }),
      createSelection({
        selectionId: 2
      })
    ];
    const openSelection = 2;
    node.find('BasicSelectionProvider').setState({selections, openSelection});

    // then
    const liNodes = node.find('li[data-test="selection-list-item"]');

    // 2 list nodes
    expect(liNodes).toHaveLength(2);

    // the second selection is open
    expect(
      liNodes
        .at(0)
        .find('Selection')
        .prop('isOpen')
    ).toBe(false);
    expect(
      liNodes
        .at(1)
        .find('Selection')
        .prop('isOpen')
    ).toBe(true);

    // data props
    selections.forEach((selection, idx) => {
      expect(
        liNodes
          .at(idx)
          .find('Selection')
          .prop('selectionId')
      ).toBe(selection.selectionId);
      expect(
        liNodes
          .at(idx)
          .find('Selection')
          .prop('instances')
      ).toEqual(selection.instancesMap);
      expect(
        liNodes
          .at(idx)
          .find('Selection')
          .prop('instanceCount')
      ).toBe(selection.totalCount);
    });
  });

  it('should render contexual message when max. number of selections is reached', () => {
    // given
    let selections = [];
    for (let i = 1; i <= 10; i++) {
      selections.push(createSelection({selectionId: i}));
    }
    node.find('BasicSelectionProvider').setState({selections});

    // then
    const expectedMessage = MESSAGES[MESSAGES_TYPE.DROP_SELECTION];
    expect(
      node.find('div[data-test="contextual-message-test"]').text()
    ).toEqual(expectedMessage);
  });

  it('should retry a selection', async () => {
    // given
    const selections = [
      createSelection({
        selectionId: 1
      })
    ];
    const openSelection = 2;
    node.find('BasicSelectionProvider').setState({selections, openSelection});

    // when
    await node.find('Selection').prop('onRetry')();

    // then
    expect(instancesApi.applyOperation).toHaveBeenCalledWith(
      OPERATION_TYPE.UPDATE_RETRIES,
      selections[0].queries
    );
  });

  it('should cancel a selection', async () => {
    // given
    const selections = [
      createSelection({
        selectionId: 1
      })
    ];
    const openSelection = 2;
    node.find('BasicSelectionProvider').setState({selections, openSelection});

    // when
    await node.find('Selection').prop('onCancel')();

    // then
    expect(instancesApi.applyOperation).toHaveBeenCalledWith(
      OPERATION_TYPE.CANCEL,
      selections[0].queries
    );
  });
});
