/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {highlightText} from './service';
import {Typeahead} from 'components';
import {shallow} from 'enzyme';

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  formatters: {
    getHighlightedText: () => 'got highlight',
  },
}));

it('should highlight strings', () => {
  const result = highlightText('highlight this');

  expect(result).toBe('got highlight');
});

it('should highlight nested highligh tags', () => {
  const result = highlightText(
    <div>
      <Typeahead.Highlight>highlight this</Typeahead.Highlight>
      not highlighted
      <h1>
        <Typeahead.Highlight>and this</Typeahead.Highlight>
      </h1>
    </div>,
    'test'
  );
  const node = shallow(<div>{result}</div>);

  expect(node).toMatchSnapshot();
});
