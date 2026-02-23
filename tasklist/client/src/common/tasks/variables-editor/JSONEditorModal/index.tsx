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
import type {editor} from 'monaco-editor';
import {isValidJSON} from 'common/utils/isValidJSON';
import {Modal} from 'common/components/Modal';
import {themeStore} from 'common/theme/theme';
import Editor from '@monaco-editor/react';
import styles from './styles.module.scss';

const getEditorOptions = (
  readOnly: boolean,
): React.ComponentProps<typeof Editor>['options'] =>
  ({
    minimap: {
      enabled: false,
    },
    fontSize: 13,
    lineHeight: 20,
    fontFamily:
      '"IBM Plex Mono", "Droid Sans Mono", "monospace", monospace, "Droid Sans Fallback"',
    formatOnPaste: !readOnly,
    formatOnType: !readOnly,
    tabSize: 2,
    wordWrap: 'on',
    scrollBeyondLastLine: false,
    readOnly,
  }) as const;

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
  readOnly?: boolean;
};

const JSONEditorModal: React.FC<Props> = observer(
  ({onClose, onSave, value = '', title, isOpen, readOnly = false}) => {
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
        onRequestSubmit={
          readOnly
            ? undefined
            : () => {
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
              }
        }
        primaryButtonDisabled={readOnly ? undefined : !isValid}
        preventCloseOnClickOutside
        primaryButtonText={
          readOnly ? undefined : t('jsonEditorApplyButtonLabel')
        }
        secondaryButtonText={
          readOnly
            ? t('jsonEditorCloseButtonLabel')
            : t('jsonEditorCancelButtonLabel')
        }
        size="lg"
        passiveModal={readOnly}
      >
        {isOpen ? (
          <Editor
            className={styles.editor}
            options={getEditorOptions(readOnly)}
            language="json"
            value={editedValue}
            onChange={
              readOnly
                ? undefined
                : (value) => {
                    const newValue = value ?? '';
                    setEditedValue(newValue);
                    setIsValid(isValidJSON(newValue));
                  }
            }
            onMount={(editor, monaco) => {
              editor.focus();
              editorRef.current = editor;
              monaco.editor.setTheme(
                themeStore.actualTheme === 'light' ? 'light' : 'vs-dark',
              );
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
