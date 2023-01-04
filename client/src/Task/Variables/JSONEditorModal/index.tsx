/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Modal} from '@carbon/react';
import {useEffect, useLayoutEffect, useRef, useState} from 'react';
import {useMonaco} from '@monaco-editor/react';
import {editor} from 'monaco-editor';
import {isValidJSON} from 'modules/utils/isValidJSON';
import {createPortal} from 'react-dom';
import {CarbonTheme} from 'modules/theme/CarbonTheme';
import {observer} from 'mobx-react-lite';
import {themeStore} from 'modules/stores/theme';
import {Editor, EditorPlaceholder} from './styled';

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
} as const;

function beautifyJSON(value: string) {
  try {
    const parsedValue = JSON.parse(value);

    return JSON.stringify(parsedValue, null, '\t');
  } catch {
    return value;
  }
}

type Props = {
  onClose: () => void;
  onSave: (value: string | undefined) => void;
  value: string | undefined;
  title: string;
  isOpen: boolean;
};

const JSONEditorModal: React.FC<Props> = observer(
  ({onClose, onSave, value = '', title, isOpen}) => {
    const [isValid, setIsValid] = useState(true);
    const [editedValue, setEditedValue] = useState('');
    const editorRef = useRef<null | editor.IStandaloneCodeEditor>(null);
    const monaco = useMonaco();

    useEffect(() => {
      setEditedValue(beautifyJSON(value));
    }, [value]);

    useLayoutEffect(() => {
      monaco?.languages.json.jsonDefaults.setDiagnosticsOptions({
        schemaValidation: 'error',
        schemaRequest: 'error',
      });
    }, [monaco]);

    useEffect(() => {
      if (isValid) {
        editorRef.current?.trigger('', 'closeMarkersNavigation', undefined);
      }
    }, [isValid]);

    return createPortal(
      <CarbonTheme>
        <Modal
          open={isOpen}
          modalHeading={title}
          onRequestClose={onClose}
          onRequestSubmit={() => {
            if (isValid) {
              onSave?.(editedValue);
            } else {
              editorRef.current?.trigger(
                '',
                'editor.action.marker.next',
                undefined,
              );
              editorRef.current?.trigger(
                '',
                'editor.action.marker.prev',
                undefined,
              );
            }
          }}
          primaryButtonDisabled={!isValid}
          preventCloseOnClickOutside
          primaryButtonText="Apply"
          secondaryButtonText="Cancel"
          size="lg"
        >
          {isOpen ? (
            <Editor
              options={options}
              language="json"
              value={editedValue}
              theme={themeStore.actualTheme === 'light' ? 'light' : 'vs-dark'}
              onChange={(value) => {
                const newValue = value ?? '';
                setEditedValue(newValue);
                setIsValid(isValidJSON(newValue));
              }}
              onMount={(editor) => {
                editor.focus();
                editorRef.current = editor;
              }}
            />
          ) : (
            <EditorPlaceholder />
          )}
        </Modal>
      </CarbonTheme>,
      document.body,
    );
  },
);

export {JSONEditorModal};
