/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
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
    const {t} = useTranslation();
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
        primaryButtonText={t('applyButtonText')}
        secondaryButtonText={t('cancelButtonText')}
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
