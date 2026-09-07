/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  CompletionContext,
  type Completion,
  type CompletionResult,
} from '@codemirror/autocomplete';
import {EditorState} from '@codemirror/state';
import {EditorView} from '@codemirror/view';
import {jsonCompletionSource} from './jsonCompletion';

type CompletionFixture = {
  text: string;
  position: number;
};

const getFixture = (value: string): CompletionFixture => {
  const position = value.indexOf('|');

  if (position === -1) {
    throw new Error('Completion fixture requires a cursor marker');
  }

  return {
    text: value.slice(0, position) + value.slice(position + 1),
    position,
  };
};

const getCompletions = async (
  value: string,
  explicit = true,
): Promise<CompletionResult | null> => {
  const {text, position} = getFixture(value);
  const context = new CompletionContext(
    EditorState.create({doc: text}),
    position,
    explicit,
  );

  return (await jsonCompletionSource(context)) ?? null;
};

const getVisibleLabel = (completion: Completion) =>
  completion.displayLabel ?? completion.label;

const applyCompletion = async (
  value: string,
  label: string,
  insertedText = '',
) => {
  const {text, position} = getFixture(value);
  const parent = document.createElement('div');
  document.body.append(parent);
  const view = new EditorView({
    parent,
    state: EditorState.create({
      doc: text,
      selection: {anchor: position},
    }),
  });

  try {
    const result = await jsonCompletionSource(
      new CompletionContext(view.state, position, true, view),
    );
    const completion = result?.options.find(
      (option) => getVisibleLabel(option) === label,
    );

    if (result === null || completion === undefined) {
      throw new Error(`Completion "${label}" was not found`);
    }

    if (insertedText !== '') {
      view.dispatch({
        changes: {from: position, insert: insertedText},
      });
    }
    const to = (result.to ?? position) + insertedText.length;

    if (typeof completion.apply === 'function') {
      completion.apply(view, completion, result.from, to);
    } else {
      const textToInsert = completion.apply ?? completion.label;
      view.dispatch({
        changes: {
          from: result.from,
          to,
          insert: textToInsert,
        },
      });
    }

    return {
      text: view.state.doc.toString(),
      selection: view.state.selection.main,
    };
  } finally {
    view.destroy();
    parent.remove();
  }
};

describe('jsonCompletionSource', () => {
  it('should provide Monaco JSON language service root snippets', async () => {
    const result = await getCompletions('|');

    expect(result?.from).toBe(0);
    expect(result?.options.map(getVisibleLabel)).toEqual([
      'Empty object',
      'Empty array',
    ]);
    expect(result?.options.map(({type}) => type)).toEqual([
      'namespace',
      'constant',
    ]);

    await expect(applyCompletion('|', 'Empty object')).resolves.toEqual({
      text: '{}',
      selection: expect.objectContaining({from: 1, to: 1}),
    });
    await expect(applyCompletion('|', 'Empty array')).resolves.toEqual({
      text: '[]',
      selection: expect.objectContaining({from: 1, to: 1}),
    });
  });

  it('should preserve schema completion filtering, replacement, and literal dollar insertion', async () => {
    const result = await getCompletions('{|');
    const schemaCompletion = result?.options[0];

    expect(result?.from).toBe(1);
    expect(result?.to).toBe(1);
    expect(schemaCompletion).toMatchObject({
      label: '"$schema"',
      displayLabel: '$schema',
      type: 'property',
      info: '',
    });

    await expect(applyCompletion('{|', '$schema')).resolves.toEqual({
      text: '{"$schema": ',
      selection: expect.objectContaining({from: 12, to: 12}),
    });
  });

  it('should provide document-derived property and value completions', async () => {
    const propertyResult = await getCompletions('[{"status":"active"},{"sta|');
    const valueResult = await getCompletions(
      '[{"status":"active"},{"status": |',
    );

    expect(propertyResult).toMatchObject({from: 22, to: 26});
    expect(propertyResult?.options.map(getVisibleLabel)).toEqual(['status']);
    expect(propertyResult?.options[0]).toMatchObject({
      label: '"status"',
      displayLabel: 'status',
      type: 'property',
      info: '',
    });
    expect(valueResult?.options.map(getVisibleLabel)).toEqual(['"active"']);
    expect(valueResult?.options[0]).toMatchObject({
      label: '"active"',
      type: 'constant',
      info: '',
    });

    await expect(
      applyCompletion('[{"status":"active"},{"sta|', 'status'),
    ).resolves.toEqual({
      text: '[{"status":"active"},{"status"',
      selection: expect.objectContaining({anchor: 30, head: 30}),
    });
    await expect(
      applyCompletion('[{"status":"active"},{"status": |', '"active"'),
    ).resolves.toEqual({
      text: '[{"status":"active"},{"status": "active"',
      selection: expect.objectContaining({anchor: 40, head: 40}),
    });
  });

  it('should preserve escaped dollars, braces, and backslashes in document values', async () => {
    const value = '[{"value":"a}b{c\\\\d$e"},{"value": |}]';
    const result = await getCompletions(value);

    expect(result?.options.map(getVisibleLabel)).toEqual(['"a}b{c\\\\d$e"']);
    await expect(applyCompletion(value, '"a}b{c\\\\d$e"')).resolves.toEqual({
      text: '[{"value":"a}b{c\\\\d$e"},{"value": "a}b{c\\\\d$e"}]',
      selection: expect.objectContaining({anchor: 46, head: 46}),
    });
  });

  it('should apply a completion to its updated range after further typing', async () => {
    await expect(
      applyCompletion('[{"status":"active"},{"sta|":null}]', 'status', 't'),
    ).resolves.toMatchObject({
      text: '[{"status":"active"},{"status":null}]',
    });
  });

  it('should preserve incomplete recovery completions and their replacement range', async () => {
    const result = await getCompletions('{invalid|');

    expect(result?.from).toBe(1);
    expect(result?.to).toBe(8);
    expect(result?.options.map(getVisibleLabel)).toEqual([
      '$schema',
      '"invalid"',
    ]);
    expect(new Set(result?.options.map(getVisibleLabel)).size).toBe(
      result?.options.length,
    );

    await expect(applyCompletion('{invalid|', '"invalid"')).resolves.toEqual({
      text: '{"invalid"',
      selection: expect.objectContaining({anchor: 10, head: 10}),
    });
  });

  it('should convert multiline language-service ranges to document offsets', async () => {
    const value = '{\n  invalid|\n}';
    const result = await getCompletions(value);

    expect(result).toMatchObject({
      from: 4,
      to: 11,
    });
    await expect(applyCompletion(value, '"invalid"')).resolves.toEqual({
      text: '{\n  "invalid"\n}',
      selection: expect.objectContaining({anchor: 13, head: 13}),
    });
  });

  it('should fall back to unique non-numeric words from the current document', async () => {
    const result = await getCompletions(
      '{"enabled": t|, "status":"active", "copy":"active", "count":42}',
    );

    expect(result?.options).toEqual([
      {label: 'enabled', apply: 'enabled', type: 'text'},
      {label: 'status', apply: 'status', type: 'text'},
      {label: 'active', apply: 'active', type: 'text'},
      {label: 'copy', apply: 'copy', type: 'text'},
      {label: 'count', apply: 'count', type: 'text'},
    ]);
    expect(result?.from).toBe(12);
    expect(result?.to).toBeUndefined();
  });

  it('should provide explicit document-word fallback inside strings', async () => {
    const value = '{"first":"hello","second":"he|';
    const result = await getCompletions(value);

    expect(result?.from).toBe(27);
    expect(result?.options.map(getVisibleLabel)).toEqual([
      'first',
      'hello',
      'second',
    ]);

    await expect(applyCompletion(value, 'hello')).resolves.toEqual({
      text: '{"first":"hello","second":"hello',
      selection: expect.objectContaining({anchor: 32, head: 32}),
    });
  });

  it('should exclude the current word when completion starts at its beginning', async () => {
    const result = await getCompletions('{"first":"hello","second":"|help"}');

    expect(result?.options.map(getVisibleLabel)).toEqual([
      'first',
      'hello',
      'second',
    ]);
  });

  it('should preserve automatic literal completion ordering', async () => {
    const value = '[true,false,t|';
    const result = await getCompletions(value, false);

    expect(result?.options.map(getVisibleLabel)).toEqual(['false', 'true']);
    await expect(applyCompletion(value, 'true')).resolves.toEqual({
      text: '[true,false,true',
      selection: expect.objectContaining({anchor: 16, head: 16}),
    });
  });

  it('should retain complete suggestions while typing and refresh structural or incomplete input', async () => {
    const result = await getCompletions('[true,false,t|', false);
    const incompleteResult = await getCompletions('{invalid|');

    if (!(result?.validFor instanceof RegExp)) {
      throw new Error('Complete suggestions need a validity range');
    }

    expect(result.validFor.test('tr')).toBe(true);
    expect(result.validFor.test('"sta')).toBe(true);
    expect(result.validFor.test('"shipping address')).toBe(true);
    expect(result.validFor.test('true,')).toBe(false);
    expect(result.validFor.test('"status": ')).toBe(false);
    expect(incompleteResult?.validFor).toBeUndefined();
  });

  it('should run for explicit requests, trigger characters, and typed words', async () => {
    await expect(getCompletions('|', false)).resolves.toBeNull();
    await expect(
      getCompletions('{"enabled": |}', false),
    ).resolves.toMatchObject({
      from: 12,
    });
    await expect(
      getCompletions('{"enabled": t|}', false),
    ).resolves.toMatchObject({
      from: 12,
    });
    await expect(getCompletions('{}]|', false)).resolves.toBeNull();
  });

  it('should suppress implicit word completion in strings and comments', async () => {
    await expect(
      getCompletions('{"first":"hello","second":"he|', false),
    ).resolves.toBeNull();
    await expect(
      getCompletions('// existing comment|', false),
    ).resolves.toBeNull();
    await expect(
      getCompletions('/* existing comment| */', false),
    ).resolves.toBeNull();
  });

  it('should allow trigger characters inside strings and comments', async () => {
    await expect(getCompletions('{"copy":"|"}', false)).resolves.toMatchObject({
      options: expect.arrayContaining([
        expect.objectContaining({label: 'copy'}),
      ]),
    });
    await expect(
      getCompletions('// existing |comment', false),
    ).resolves.toMatchObject({
      options: expect.arrayContaining([
        expect.objectContaining({label: 'existing'}),
      ]),
    });
  });

  it('should return no suggestions when neither provider has a result', async () => {
    await expect(getCompletions('//|')).resolves.toBeNull();
  });

  it('should never request a schema over the network', async () => {
    const fetchSpy = vi
      .spyOn(globalThis, 'fetch')
      .mockRejectedValue(new Error('Unexpected schema request'));

    try {
      await getCompletions(
        '{"$schema":"https://example.invalid/schema.json","value": |}',
      );

      expect(fetchSpy).not.toHaveBeenCalled();
    } finally {
      fetchSpy.mockRestore();
    }
  });
});
