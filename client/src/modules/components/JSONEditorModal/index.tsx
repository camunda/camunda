/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import Modal, {SIZES} from 'modules/components/Modal';
import {useEffect, useRef, useState} from 'react';
import {Body} from './styled';
import {observer} from 'mobx-react';
import {JSONEditor} from 'modules/components/JSONEditor';
import {U} from 'ts-toolbelt';

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
        setEditedValue(value);
      } else {
        setEditedValue('');
      }
    }, [isVisible, value]);

    useEffect(() => {
      if (isValid) {
        editorRef.current?.hideMarkers();
      }
    }, [isValid]);

    return (
      <>
        <Modal
          onModalClose={() => {
            onClose?.();
          }}
          size={SIZES.BIG}
          isVisible={isVisible}
          preventKeyboardEvents
        >
          <Modal.Header aria-label={title}>{title}</Modal.Header>
          <Body data-testid="json-editor-container">
            <JSONEditor
              value={editedValue}
              onChange={setEditedValue}
              readOnly={readOnly}
              onValidate={setIsValid}
              onMount={(editor) => {
                editorRef.current = editor;
              }}
            />
          </Body>
          <Modal.Footer>
            {readOnly ? (
              <Modal.PrimaryButton
                onClick={() => {
                  onClose?.();
                }}
                type="button"
              >
                Close
              </Modal.PrimaryButton>
            ) : (
              <>
                <Modal.SecondaryButton
                  onClick={() => {
                    onClose?.();
                  }}
                  type="button"
                >
                  Cancel
                </Modal.SecondaryButton>
                <Modal.PrimaryButton
                  onClick={() => {
                    if (isValid) {
                      onApply?.(editedValue);
                    } else {
                      editorRef.current?.showMarkers();
                    }
                  }}
                  type="button"
                >
                  Apply
                </Modal.PrimaryButton>
              </>
            )}
          </Modal.Footer>
        </Modal>
      </>
    );
  }
);

export {JSONEditorModal};
