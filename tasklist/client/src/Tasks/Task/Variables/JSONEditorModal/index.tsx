/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {useEffect, useRef, useState} from 'react';
import {observer} from 'mobx-react-lite';
import {editor} from 'monaco-editor';
import {isValidJSON} from 'modules/utils/isValidJSON';
import {Modal} from 'modules/components/Modal';
import {themeStore} from 'modules/stores/theme';
import Editor from '@monaco-editor/react';
import styles from './styles.module.scss';

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

    useEffect(() => {
      setEditedValue(beautifyJSON(value));
    }, [value]);

    return (
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
            className={styles.editor}
            options={options}
            language="json"
            value={editedValue}
            onChange={(value) => {
              const newValue = value ?? '';
              setEditedValue(newValue);
              setIsValid(isValidJSON(newValue));
            }}
            onMount={(editor, monaco) => {
              editor.focus();
              editorRef.current = editor;
              monaco.editor.setTheme(
                themeStore.actualTheme === 'light' ? 'light' : 'vs-dark',
              );
              monaco.languages.json.jsonDefaults.setDiagnosticsOptions({
                schemaValidation: 'error',
                schemaRequest: 'error',
              });
            }}
          />
        ) : (
          <div className={styles.editor} />
        )}
      </Modal>
    );
  },
);

export {JSONEditorModal};
