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

const options: React.ComponentProps<typeof Editor>['options'] = {
  minimap: {
    enabled: false,
  },
  fontSize: 13,
  lineHeight: 20,
  fontFamily:
    '"IBM Plex Mono", "Droid Sans Mono", "monospace", monospace, "Droid Sans Fallback"',
  formatOnPaste: true,
  formatOnType: true,
  tabSize: 2,
  wordWrap: 'on',
  scrollBeyondLastLine: false,
};

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
    const {
      state: {selectedTheme},
    } = currentTheme;
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
          theme={selectedTheme === 'dark' ? 'vs-dark' : 'light'}
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
