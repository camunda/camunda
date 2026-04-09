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
import {Edit} from '@carbon/react/icons';
import {Toolbar} from './styled';
import {CopyButton} from '../CopyButton';

type EditorFirstParam = Parameters<
  NonNullable<React.ComponentProps<typeof JSONEditor>['onMount']>
>[0];

const JSONEditor = lazy(async () => {
  const [{loadMonaco}, {JSONEditor}] = await Promise.all([
    import('modules/loadMonaco'),
    import('modules/components/JSONEditor'),
  ]);

  loadMonaco();

  return {default: JSONEditor};
});

type Props = {
  value: string;
  isVisible: boolean;
  onClose?: () => void;
  onApply?: (value: string | undefined) => void;
  title?: string;
  readOnly?: boolean;
};

const JSONEditorModal: React.FC<Props> = observer(
  ({value, isVisible, onClose, onApply, title, readOnly = false}) => {
    const [editedValue, setEditedValue] = useState(value);
    const [isValid, setIsValid] = useState(true);
    const [isInEditMode, setIsInEditMode] = useState(!readOnly);
    const editorRef = useRef<EditorFirstParam | null>(null);

    useEffect(() => {
      if (isVisible) {
        setEditedValue(beautifyJSON(value));
      } else {
        setEditedValue('');
        setIsValid(true);
      }
    }, [isVisible, value]);

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
    const canSwitchToEdit = readOnly && !isInEditMode && onApply !== undefined;

    return (
      <Modal
        open={isVisible}
        modalHeading={title}
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
          {canSwitchToEdit && (
            <Button
              kind="ghost"
              size="sm"
              renderIcon={Edit}
              iconDescription="Edit"
              hasIconOnly
              onClick={() => setIsInEditMode(true)}
            />
          )}
          <CopyButton value={editedValue} />
        </Toolbar>
        <Suspense>
          <JSONEditor
            value={editedValue}
            onChange={setEditedValue}
            readOnly={isReadOnly}
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

export {JSONEditorModal};
