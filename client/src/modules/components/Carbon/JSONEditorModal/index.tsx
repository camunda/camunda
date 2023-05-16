/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useRef, useState} from 'react';
import {observer} from 'mobx-react';
import {JSONEditor} from 'modules/components/Carbon/JSONEditor';
import {U} from 'ts-toolbelt';
import {beautifyJSON} from 'modules/utils/editor/beautifyJSON';
import {Modal} from '@carbon/react';

type EditorFirstParam = Parameters<
  U.NonNullable<React.ComponentProps<typeof JSONEditor>['onMount']>
>[0];

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
      if (isValid) {
        editorRef.current?.hideMarkers();
      }
    }, [isValid]);

    if (!isVisible) {
      return null;
    }

    return (
      <Modal
        open={isVisible}
        modalHeading={title}
        onRequestClose={() => {
          onClose?.();
        }}
        onRequestSubmit={() => {
          if (isValid) {
            onApply?.(editedValue);
          } else {
            editorRef.current?.showMarkers();
          }
        }}
        size="lg"
        primaryButtonText="Apply"
        secondaryButtonText="Cancel"
        passiveModal={readOnly}
        preventCloseOnClickOutside
      >
        <JSONEditor
          value={editedValue}
          onChange={setEditedValue}
          readOnly={readOnly}
          onValidate={setIsValid}
          onMount={(editor) => {
            editorRef.current = editor;
          }}
        />
      </Modal>
    );
  }
);

export {JSONEditorModal};
