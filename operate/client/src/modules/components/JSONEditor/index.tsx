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
import {options} from 'modules/utils/editor/options';

type Props = {
  value: string;
  onChange?: (value: string) => void;
  readOnly?: boolean;
  onValidate?: (isValid: boolean) => void;
  onMount?: (editor: {
    showMarkers: () => void;
    hideMarkers: () => void;
  }) => void;
  height?: string;
  width?: string;
};

const JSONEditor: React.FC<Props> = observer(
  ({
    value,
    onChange,
    readOnly = false,
    onValidate = () => {},
    onMount = () => {},
    height = '60vh',
    width = '100%',
  }) => {
    return (
      <>
        <EditorStyles />
        <Editor
          options={{...options, readOnly}}
          language="json"
          value={value}
          height={height}
          width={width}
          theme={currentTheme.theme === 'dark' ? 'vs-dark' : 'light'}
          onChange={(value) => {
            onChange?.(value ?? '');
          }}
          onMount={(editor) => {
            editor.focus();

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

export {JSONEditor};
