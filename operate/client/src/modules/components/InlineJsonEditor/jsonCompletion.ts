/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  pickedCompletion,
  snippet,
  type Completion,
  type CompletionResult,
  type CompletionSource,
} from '@codemirror/autocomplete';
import {Transaction} from '@codemirror/state';
import type {EditorView} from '@codemirror/view';
import {
  ClientCapabilities,
  CompletionItemKind,
  InsertTextFormat,
  getLanguageService,
  type CompletionItem,
  type DocumentLanguageSettings,
  type LanguageSettings,
  type MarkupContent,
  type Range,
} from 'vscode-json-languageservice';
import {TextDocument} from 'vscode-languageserver-textdocument';

const DOCUMENT_URI = 'inmemory://model/inline-json.json';
const JSON_WORD_PATTERN = /(-?\d*\.\d\w*)|([^[\]{}:",\s]+)/g;
const TRIGGER_CHARACTERS = new Set([' ', ':', '"']);

const languageService = getLanguageService({
  clientCapabilities: ClientCapabilities.LATEST,
  workspaceContext: {
    resolveRelativePath: (relativePath, resource) =>
      new URL(relativePath, resource).toString(),
  },
});

const languageSettings = {
  validate: true,
  allowComments: true,
  schemas: [],
  schemaRequest: 'error',
  schemaValidation: 'error',
  comments: 'error',
  trailingCommas: 'error',
} satisfies LanguageSettings & DocumentLanguageSettings;

languageService.configure(languageSettings);

const completionTypes: Partial<Record<CompletionItemKind, string>> = {
  [CompletionItemKind.Text]: 'text',
  [CompletionItemKind.Method]: 'method',
  [CompletionItemKind.Function]: 'function',
  [CompletionItemKind.Constructor]: 'function',
  [CompletionItemKind.Field]: 'property',
  [CompletionItemKind.Variable]: 'variable',
  [CompletionItemKind.Class]: 'class',
  [CompletionItemKind.Interface]: 'interface',
  [CompletionItemKind.Module]: 'namespace',
  [CompletionItemKind.Property]: 'property',
  [CompletionItemKind.Unit]: 'constant',
  [CompletionItemKind.Value]: 'constant',
  [CompletionItemKind.Enum]: 'enum',
  [CompletionItemKind.Keyword]: 'keyword',
  [CompletionItemKind.Snippet]: 'text',
  [CompletionItemKind.Color]: 'constant',
  [CompletionItemKind.File]: 'text',
  [CompletionItemKind.Reference]: 'variable',
  [CompletionItemKind.Folder]: 'namespace',
  [CompletionItemKind.EnumMember]: 'enum',
  [CompletionItemKind.Constant]: 'constant',
  [CompletionItemKind.Struct]: 'type',
  [CompletionItemKind.Event]: 'variable',
  [CompletionItemKind.Operator]: 'keyword',
  [CompletionItemKind.TypeParameter]: 'type',
};

type WordRange = {
  from: number;
  to: number;
  word: string;
};

const getWordRanges = (text: string): WordRange[] => {
  const ranges: WordRange[] = [];

  for (const match of text.matchAll(JSON_WORD_PATTERN)) {
    if (match.index !== undefined) {
      ranges.push({
        from: match.index,
        to: match.index + match[0].length,
        word: match[0],
      });
    }
  }

  return ranges;
};

const getCurrentWord = (ranges: WordRange[], position: number) =>
  ranges.find(({from, to}) => from <= position && position <= to);

const getSyntacticContext = (
  text: string,
  position: number,
): 'comment' | 'string' | 'other' => {
  let context: 'blockComment' | 'lineComment' | 'string' | 'other' = 'other';

  for (let index = 0; index < position; index++) {
    const character = text.charAt(index);
    const nextCharacter = text.charAt(index + 1);

    if (context === 'string') {
      if (character === '\\') {
        index++;
      } else if (character === '"') {
        context = 'other';
      }
      continue;
    }

    if (context === 'lineComment') {
      if (character === '\n' || character === '\r') {
        context = 'other';
      }
      continue;
    }

    if (context === 'blockComment') {
      if (character === '*' && nextCharacter === '/') {
        context = 'other';
        index++;
      }
      continue;
    }

    if (character === '"') {
      context = 'string';
    } else if (character === '/' && nextCharacter === '/') {
      context = 'lineComment';
      index++;
    } else if (character === '/' && nextCharacter === '*') {
      context = 'blockComment';
      index++;
    }
  }

  return context === 'string'
    ? 'string'
    : context === 'other'
      ? 'other'
      : 'comment';
};

const isEligibleContext = (
  text: string,
  position: number,
  explicit: boolean,
) => {
  if (explicit) {
    return true;
  }

  const previousCharacter = text[position - 1];
  if (
    previousCharacter !== undefined &&
    TRIGGER_CHARACTERS.has(previousCharacter)
  ) {
    return true;
  }

  if (getSyntacticContext(text, position) !== 'other') {
    return false;
  }

  return getCurrentWord(getWordRanges(text), position) !== undefined;
};

const getDocumentation = (
  documentation: string | MarkupContent | undefined,
) => {
  if (typeof documentation === 'string') {
    return documentation;
  }

  return documentation?.value;
};

const getOffsets = (document: TextDocument, range: Range) => ({
  from: document.offsetAt(range.start),
  to: document.offsetAt(range.end),
});

const escapeCodeMirrorLiteral = (value: string) =>
  value.replace(/[{}]/g, '\\$&');

const convertLspSnippet = (value: string) => {
  let template = '';
  let text = '';
  let hasTabStop = false;
  let position = 0;

  while (position < value.length) {
    const character = value.charAt(position);

    if (character === '\\') {
      const escapedCharacter = value.charAt(position + 1);
      if (
        escapedCharacter === '\\' ||
        escapedCharacter === '$' ||
        escapedCharacter === '}'
      ) {
        template += escapeCodeMirrorLiteral(escapedCharacter);
        text += escapedCharacter;
        position += 2;
        continue;
      }

      template += '\\';
      text += '\\';
      position++;
      continue;
    }

    if (character === '$') {
      const tabStop = /^\$(\d+)/.exec(value.slice(position));
      if (tabStop !== null) {
        template += `\${${tabStop[1]}}`;
        hasTabStop = true;
        position += tabStop[0].length;
        continue;
      }

      const placeholder = /^\$\{(\d+)(?::([^{}]*))?\}/.exec(
        value.slice(position),
      );
      if (placeholder !== null) {
        const defaultText =
          placeholder[2] === undefined
            ? ''
            : placeholder[2].replace(/\\([\\$}])/g, '$1');
        template += `\${${placeholder[1]}${
          placeholder[2] === undefined ? '' : `:${defaultText}`
        }}`;
        text += defaultText;
        hasTabStop = true;
        position += placeholder[0].length;
        continue;
      }
    }

    template += escapeCodeMirrorLiteral(character);
    text += character;
    position++;
  }

  return {template, text, hasTabStop};
};

const getTextEdit = (
  document: TextDocument,
  item: CompletionItem,
  fallbackFrom: number,
  fallbackTo: number,
) => {
  if (item.textEdit === undefined) {
    return {
      from: fallbackFrom,
      to: fallbackTo,
      text: item.insertText ?? item.label,
    };
  }

  if ('range' in item.textEdit) {
    return {
      ...getOffsets(document, item.textEdit.range),
      text: item.textEdit.newText,
    };
  }

  return {
    ...getOffsets(document, item.textEdit.insert),
    text: item.textEdit.newText,
  };
};

const applyPlainText = (
  view: EditorView,
  completion: Completion,
  from: number,
  to: number,
  text: string,
) => {
  view.dispatch({
    changes: {from, to, insert: text},
    selection: {anchor: from + text.length},
    annotations: [
      pickedCompletion.of(completion),
      Transaction.userEvent.of('input.complete'),
    ],
    scrollIntoView: true,
  });
};

const getLanguageServiceCompletions = (
  document: TextDocument,
  items: CompletionItem[],
  fallbackFrom: number,
  fallbackTo: number,
) => {
  const edits = items.map((item) => ({
    item,
    edit: getTextEdit(document, item, fallbackFrom, fallbackTo),
  }));
  const resultFrom = Math.min(...edits.map(({edit}) => edit.from));
  const options = edits.map(({item, edit}) => {
    const filterLabel = item.filterText ?? item.label;
    const completion: Completion = {
      label: filterLabel,
      ...(filterLabel === item.label ? {} : {displayLabel: item.label}),
      ...(item.sortText === undefined ? {} : {sortText: item.sortText}),
      ...(item.detail === undefined ? {} : {detail: item.detail}),
      ...(item.documentation === undefined
        ? {}
        : {info: getDocumentation(item.documentation)}),
      ...(item.kind === undefined ? {} : {type: completionTypes[item.kind]}),
      ...(item.commitCharacters === undefined
        ? {}
        : {commitCharacters: item.commitCharacters}),
    };

    const convertedSnippet =
      item.insertTextFormat === InsertTextFormat.Snippet
        ? convertLspSnippet(edit.text)
        : undefined;
    const apply =
      convertedSnippet?.hasTabStop === true
        ? snippet(convertedSnippet.template)
        : (
            view: EditorView,
            selectedCompletion: Completion,
            from: number,
            to: number,
          ) =>
            applyPlainText(
              view,
              selectedCompletion,
              from,
              to,
              convertedSnippet?.text ?? edit.text,
            );

    return {
      ...completion,
      apply: (
        view: EditorView,
        selectedCompletion: Completion,
        from: number,
        to: number,
      ) =>
        apply(
          view,
          selectedCompletion,
          from + edit.from - resultFrom,
          to + edit.to - fallbackTo,
        ),
    };
  });

  return {from: resultFrom, to: fallbackTo, options};
};

const getWordCompletions = (
  text: string,
  position: number,
): CompletionResult | null => {
  const ranges = getWordRanges(text);
  const currentWord = getCurrentWord(ranges, position);
  const seen = new Set<string>();
  const options: Completion[] = [];

  for (const {word} of ranges) {
    if (
      word === currentWord?.word ||
      !Number.isNaN(Number(word)) ||
      seen.has(word)
    ) {
      continue;
    }

    seen.add(word);
    options.push({
      label: word,
      apply: word,
      type: 'text',
    });

    if (seen.size > 10_000) {
      break;
    }
  }

  if (options.length === 0) {
    return null;
  }

  return {
    from: currentWord?.from ?? position,
    options,
    validFor: /^[^[\]{}:",\s]*$/,
  };
};

const jsonCompletionSource: CompletionSource = async (context) => {
  const text = context.state.doc.toString();

  if (!isEligibleContext(text, context.pos, context.explicit)) {
    return null;
  }

  const document = TextDocument.create(DOCUMENT_URI, 'json', 1, text);
  const completionList = await languageService.doComplete(
    document,
    document.positionAt(context.pos),
    languageService.parseJSONDocument(document),
  );

  if (context.aborted) {
    return null;
  }

  if (completionList === null || completionList.items.length === 0) {
    return getWordCompletions(text, context.pos);
  }

  const currentWord = getCurrentWord(getWordRanges(text), context.pos);
  return {
    ...getLanguageServiceCompletions(
      document,
      completionList.items,
      currentWord?.from ?? context.pos,
      context.pos,
    ),
    // Keep complete lists usable while typing; incomplete lists need fresh results.
    validFor: completionList.isIncomplete
      ? undefined
      : /^(?:"(?:[^"\\\r\n]|\\.)*|[^[\]{}:",\s]*)$/,
  };
};

export {jsonCompletionSource};
