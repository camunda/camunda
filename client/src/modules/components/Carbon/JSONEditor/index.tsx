/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import Editor, {useMonaco} from '@monaco-editor/react';
import {observer} from 'mobx-react-lite';
import {currentTheme} from 'modules/stores/currentTheme';
import {useLayoutEffect} from 'react';
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
};

const JSONEditor: React.FC<Props> = observer(
  ({
    value,
    onChange,
    readOnly = false,
    onValidate = () => {},
    onMount = () => {},
  }) => {
    const monaco = useMonaco();

    useLayoutEffect(() => {
      monaco?.languages.json.jsonDefaults.setDiagnosticsOptions({
        schemaValidation: 'error',
        schemaRequest: 'error',
      });
    }, [monaco]);

    return (
      <>
        <EditorStyles />
        <Editor
          options={{...options, readOnly}}
          language="json"
          value={value}
          height="60vh"
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
  }
);

export {JSONEditor, useMonaco};
