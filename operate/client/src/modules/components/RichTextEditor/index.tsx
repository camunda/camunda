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
import {type editor, json, KeyCode} from 'monaco-editor';
import {useEffect, useMemo, type ReactNode} from 'react';

type EditorHandle = {
  showMarkers: () => void;
  hideMarkers: () => void;
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
  jsonSchema?: object;
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
    jsonSchema,
  }) => {
    const editorOptions = {
      ...defaultOptions,
      ...options,
      readOnly,
    };

    const {modelPath, schemaUri} = useMemo(() => {
      const id = Math.random().toString(36).slice(2, 10);
      return {
        modelPath: `inmemory://rich-text-editor/${id}.json`,
        schemaUri: `inmemory://rich-text-editor/${id}.schema.json`,
      };
    }, []);
    useEffect(() => {
      return () => {
        const current = json.jsonDefaults.diagnosticsOptions;
        if (!current.schemas?.some((s) => s.uri === schemaUri)) {
          return;
        }
        json.jsonDefaults.setDiagnosticsOptions({
          ...current,
          schemas: current.schemas.filter((s) => s.uri !== schemaUri),
        });
      };
    }, [schemaUri]);

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
          path={jsonSchema ? modelPath : undefined}
          theme={currentTheme.theme === 'dark' ? 'vs-dark' : 'light'}
          onChange={(value) => {
            onChange?.(value ?? '');
          }}
          onMount={(editor) => {
            editor.focus();

            if (jsonSchema && language === 'json') {
              const current = json.jsonDefaults.diagnosticsOptions;
              json.jsonDefaults.setDiagnosticsOptions({
                ...current,
                schemas: [
                  ...(current.schemas ?? []).filter((s) => s.uri !== schemaUri),
                  {
                    uri: schemaUri,
                    fileMatch: [modelPath],
                    schema: jsonSchema,
                  },
                ],
              });
            }

            editor.onKeyDown((e) => {
              if (e.keyCode && e.keyCode === KeyCode.Escape) {
                if (document.activeElement instanceof HTMLElement) {
                  document.activeElement?.blur();
                }
              }
            });

            onMount({
              showMarkers: () => {
                editor.trigger('', 'editor.action.marker.next', undefined);
                editor.trigger('', 'editor.action.marker.prev', undefined);
              },
              hideMarkers: () => {
                editor.trigger('', 'closeMarkersNavigation', undefined);
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
