/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense, useEffect, useRef, useState} from 'react';
import {observer} from 'mobx-react';
import {beautifyJSON} from 'modules/utils/editor/beautifyJSON';
import {Button, Modal} from '@carbon/react';
import {Edit, View} from '@carbon/react/icons';
import {Toolbar} from './styled';
import {CopyButton} from '../CopyButton';

type EditorFirstParam = Parameters<
  NonNullable<React.ComponentProps<typeof RichTextEditor>['onMount']>
>[0];

const RichTextEditor = lazy(async () => {
  const [{loadMonaco}, {RichTextEditor}] = await Promise.all([
    import('modules/loadMonaco'),
    import('modules/components/RichTextEditor'),
  ]);

  loadMonaco();

  return {default: RichTextEditor};
});

type Props = {
  /** @default "json" */
  language?: 'json' | 'markdown';
  value: string;
  isVisible: boolean;
  onClose?: () => void;
  onApply?: (value: string | undefined) => void;
  title?: string;
  editModeTitle?: string;
  readOnly?: boolean;
  allowModeToggle?: boolean;
};

const RichTextEditorModal: React.FC<Props> = observer(
  ({
    language = 'json',
    value,
    isVisible,
    onClose,
    onApply,
    title,
    editModeTitle,
    readOnly = false,
    allowModeToggle = false,
  }) => {
    const [editedValue, setEditedValue] = useState(value);
    const [isValid, setIsValid] = useState(true);
    const [isInEditMode, setIsInEditMode] = useState(!readOnly);
    const editorRef = useRef<EditorFirstParam | null>(null);

    useEffect(() => {
      if (isVisible) {
        setEditedValue(language === 'json' ? beautifyJSON(value) : value);
      } else {
        setEditedValue('');
        setIsValid(true);
      }
    }, [isVisible, value, language]);

    useEffect(() => {
      setIsInEditMode(!readOnly);
    }, [readOnly, isVisible]);

    useEffect(() => {
      if (isValid) {
        editorRef.current?.hideMarkers();
      }
    }, [isValid]);

    if (!isVisible) {
      return null;
    }

    const isReadOnly = readOnly && !isInEditMode;
    const canToggleMode = readOnly && allowModeToggle;

    const toggleEditMode = () => {
      if (!isInEditMode) {
        setIsInEditMode(true);
        return;
      }

      setIsInEditMode(false);
      setEditedValue(language === 'json' ? beautifyJSON(value) : value);
    };

    return (
      <Modal
        open={isVisible}
        modalHeading={isInEditMode && editModeTitle ? editModeTitle : title}
        onRequestClose={() => {
          onClose?.();
        }}
        onRequestSubmit={() => {
          if (isValid) {
            onApply?.(JSON.stringify(JSON.parse(editedValue)));
          } else {
            editorRef.current?.showMarkers();
          }
        }}
        size="lg"
        primaryButtonText="Apply"
        secondaryButtonText="Cancel"
        passiveModal={isReadOnly}
        preventCloseOnClickOutside
      >
        <Toolbar>
          {canToggleMode && (
            <Button
              kind="ghost"
              size="sm"
              renderIcon={!isInEditMode ? Edit : View}
              iconDescription={`Switch to ${!isInEditMode ? 'edit' : 'view'} mode`}
              onClick={toggleEditMode}
            >
              {!isInEditMode ? 'Edit' : 'View'}
            </Button>
          )}
          <CopyButton value={editedValue} />
        </Toolbar>
        <Suspense>
          <RichTextEditor
            value={editedValue}
            onChange={setEditedValue}
            readOnly={isReadOnly}
            language={language}
            onValidate={setIsValid}
            onMount={(editor) => {
              editorRef.current = editor;
            }}
          />
        </Suspense>
      </Modal>
    );
  },
);

export {RichTextEditorModal};
