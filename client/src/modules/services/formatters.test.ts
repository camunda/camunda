/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getHighlightedText} from './formatters';

describe('getHighlightedText', () => {
  it('Should wrap the highlighted text in a span and give it textBold class', () => {
    const results = getHighlightedText('test text', 'text') as JSX.Element[];
    expect(results[1]?.props.children).toBe('text');
    expect(results[1]?.props.className).toBe('textBold');
  });

  it('Should return the same text as string if the highlight is empty', () => {
    const results = getHighlightedText('test text', '');
    expect(results).toBe('test text');
  });

  it('the regex should match only from the start of the text if specified', () => {
    const notMatch = getHighlightedText('test text', 'text', true);
    expect(notMatch.length).toBe(1);
    const results = getHighlightedText('test text', 'test', true) as JSX.Element[];
    expect(results[1]?.props.children).toBe('test');
    expect(results[1]?.props.className).toBe('textBold');
  });

  it('should work with special characters', () => {
    const results = getHighlightedText('test)', ')') as JSX.Element[];
    expect(results[1]?.props.children).toBe(')');
    expect(results[1]?.props.className).toBe('textBold');
  });
});
