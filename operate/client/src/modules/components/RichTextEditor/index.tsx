/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import Editor from '@monaco-editor/react';
import {observer} from 'mobx-react-lite';
import {currentTheme} from 'modules/stores/currentTheme';
import {EditorStyles} from './styled';
import {options as defaultOptions} from 'modules/utils/editor/options';
import {type editor, KeyCode, MarkerSeverity} from 'monaco-editor';
import type {ReactNode} from 'react';

type EditorHandle = {
  showMarkers: () => void;
  hideMarkers: () => void;
  setCustomMarkers: (
    markers: Array<{
      message: string;
      startLineNumber: number;
      startColumn: number;
      endLineNumber: number;
      endColumn: number;
      severity?: 'error' | 'warning' | 'info';
    }>,
  ) => void;
};

type Props = {
  /** @default "json" */
  language?: 'json' | 'markdown';
  value: string;
  readOnly?: boolean;
  onChange?: (value: string) => void;
  onValidate?: (isValid: boolean) => void;
  onMount?: (editor: EditorHandle) => void;
  height?: string;
  width?: string;
  options?: editor.IStandaloneEditorConstructionOptions;
  loading?: ReactNode;
};

const RichTextEditor: React.FC<Props> = observer(
  ({
    language = 'json',
    value,
    onChange,
    readOnly = false,
    onValidate = () => {},
    onMount = () => {},
    height = '60vh',
    width = '100%',
    options = {},
    loading,
  }) => {
    const editorOptions = {
      ...defaultOptions,
      ...options,
      readOnly,
    };

    return (
      <>
        <EditorStyles />
        <Editor
          loading={loading}
          options={editorOptions}
          language={language}
          value={value}
          height={height}
          width={width}
          theme={currentTheme.theme === 'dark' ? 'vs-dark' : 'light'}
          onChange={(value) => {
            onChange?.(value ?? '');
          }}
          onMount={(editor, monaco) => {
            editor.focus();

            editor.onKeyDown((e) => {
              if (e.keyCode && e.keyCode === KeyCode.Escape) {
                if (document.activeElement instanceof HTMLElement) {
                  document.activeElement?.blur();
                }
              }
            });

            const severityMap = {
              error: MarkerSeverity.Error,
              warning: MarkerSeverity.Warning,
              info: MarkerSeverity.Info,
            } as const;

            onMount({
              showMarkers: () => {
                editor.trigger('', 'editor.action.marker.next', undefined);
                editor.trigger('', 'editor.action.marker.prev', undefined);
              },
              hideMarkers: () => {
                editor.trigger('', 'closeMarkersNavigation', undefined);
              },
              setCustomMarkers: (markers) => {
                const model = editor.getModel();
                if (model) {
                  monaco.editor.setModelMarkers(
                    model,
                    'custom',
                    markers.map((m) => ({
                      ...m,
                      severity: severityMap[m.severity ?? 'error'],
                    })),
                  );
                }
              },
            });
          }}
          onValidate={(markers) => {
            onValidate(markers.length === 0);
          }}
        />
      </>
    );
  },
);

export {RichTextEditor};
export type {EditorHandle};
