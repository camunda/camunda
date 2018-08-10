import React from 'react';
import {shallow} from 'enzyme';
import {mockResolvedAsyncFn} from 'modules/testUtils';

import SelectionList from './SelectionList';
import * as selectionsApi from 'modules/api/selections/selections';

selectionsApi.batchRetry = mockResolvedAsyncFn();

describe('SelectionList', () => {
  let node;
  let selections;

  beforeEach(async () => {
    selections = [];
    node = shallow(
      <SelectionList
        selections={selections}
        onDelete={jest.fn()}
        onToggle={jest.fn()}
        onChange={jest.fn()}
      />
    );
  });

  it('should call the retrySelection with the selection query', () => {
    node.instance().retrySelection();
    expect(selectionsApi.batchRetry).toHaveBeenCalled();
  });
});
