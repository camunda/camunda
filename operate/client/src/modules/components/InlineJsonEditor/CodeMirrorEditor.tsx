/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  useEffect,
  useEffectEvent,
  useLayoutEffect,
  useMemo,
  useRef,
} from 'react';
import {defaultKeymap, history, historyKeymap} from '@codemirror/commands';
import {
  autocompletion,
  clearSnippet,
  closeCompletion,
  snippetKeymap,
} from '@codemirror/autocomplete';
import {json} from '@codemirror/lang-json';
import {HighlightStyle, syntaxHighlighting} from '@codemirror/language';
import {
  Annotation,
  Compartment,
  EditorState,
  Prec,
  Transaction,
} from '@codemirror/state';
import {
  EditorView,
  keymap,
  placeholder as showPlaceholder,
  tooltips,
  type ViewUpdate,
} from '@codemirror/view';
import {tags} from '@lezer/highlight';
import {jsonCompletionSource} from './jsonCompletion';
import {CompletionStyles} from './styled';

const externalUpdate = Annotation.define<boolean>();
const editorConfiguration = new Compartment();
const jsonHighlightStyle = HighlightStyle.define([
  {
    tag: tags.propertyName,
    color: 'var(--cds-text-secondary)',
  },
  {
    tag: [tags.string, tags.special(tags.string)],
    color: 'var(--cds-support-success)',
  },
  {
    tag: [tags.number, tags.bool, tags.null],
    color: 'var(--cds-link-primary)',
  },
]);
const extensions = [
  json(),
  history(),
  snippetKeymap.of([]),
  Prec.highest(
    keymap.of([
      {
        key: 'Escape',
        run: (view) => {
          closeCompletion(view);
          clearSnippet(view);
          view.contentDOM.blur();
          return true;
        },
      },
    ]),
  ),
  autocompletion({
    override: [jsonCompletionSource],
    activateOnTypingDelay: 10,
    interactionDelay: 0,
    tooltipClass: () => 'inline-json-completions',
  }),
  keymap.of([...defaultKeymap, ...historyKeymap]),
  syntaxHighlighting(jsonHighlightStyle),
  EditorState.tabSize.of(2),
  EditorView.lineWrapping,
];

type Props = {
  value: string;
  label: string;
  placeholder: string;
  id?: string;
  autoFocus?: boolean;
  invalid?: boolean;
  onChange: (value: string) => void;
};

const CodeMirrorEditor: React.FC<Props> = ({
  value,
  label,
  placeholder,
  id,
  autoFocus,
  invalid,
  onChange,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const viewRef = useRef<EditorView | null>(null);
  const configuration = useMemo(
    () => [
      EditorView.contentAttributes.of({
        ...(id === undefined ? {} : {id}),
        'aria-label': label,
        'aria-invalid': invalid ? 'true' : 'false',
      }),
      showPlaceholder(placeholder),
    ],
    [id, invalid, label, placeholder],
  );
  const handleUpdate = useEffectEvent((update: ViewUpdate) => {
    if (
      update.docChanged &&
      !update.transactions.some((transaction) =>
        transaction.annotation(externalUpdate),
      )
    ) {
      onChange(update.state.doc.toString());
    }
  });

  useLayoutEffect(() => {
    if (containerRef.current === null) {
      return;
    }

    const view = viewRef.current;

    if (view === null) {
      viewRef.current = new EditorView({
        parent: containerRef.current,
        doc: value,
        extensions: [
          ...extensions,
          tooltips({parent: containerRef.current.ownerDocument.body}),
          editorConfiguration.of(configuration),
          EditorView.updateListener.of((update) => handleUpdate(update)),
        ],
      });
      return;
    }

    const valueChanged = view.state.doc.toString() !== value;
    const configurationChanged =
      editorConfiguration.get(view.state) !== configuration;

    if (valueChanged || configurationChanged) {
      view.dispatch({
        changes: valueChanged
          ? {from: 0, to: view.state.doc.length, insert: value}
          : undefined,
        effects: configurationChanged
          ? editorConfiguration.reconfigure(configuration)
          : [],
        annotations: [
          externalUpdate.of(true),
          Transaction.addToHistory.of(false),
        ],
      });
    }
  }, [configuration, value]);

  useLayoutEffect(() => {
    if (autoFocus) {
      viewRef.current?.focus();
    }
  }, [autoFocus]);

  useEffect(
    () => () => {
      viewRef.current?.destroy();
      viewRef.current = null;
    },
    [],
  );

  return (
    <>
      <CompletionStyles />
      <div ref={containerRef} data-testid="code-mirror-editor" />
    </>
  );
};

export {CodeMirrorEditor};
