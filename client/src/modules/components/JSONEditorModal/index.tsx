/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import Modal, {SIZES} from 'modules/components/Modal';
import {useLayoutEffect, useRef, useState} from 'react';
import JSONEditor from 'jsoneditor';
import 'jsoneditor/dist/jsoneditor.css';
import {JSONEditorStyles, Body} from './styled';
import 'brace/theme/tomorrow_night';
import 'brace/theme/tomorrow';
import {observer} from 'mobx-react';
import {currentTheme} from 'modules/stores/currentTheme';
import {isValidJSON} from 'modules/utils';
type Props = {
  onClose?: () => void;
  onSave?: (value: string | undefined) => void;
  value: string | undefined;
  title?: string;
  isModalVisible: boolean;
};

const JSONEditorModal: React.FC<Props> = observer(
  ({onClose, onSave, value = '', isModalVisible, title}) => {
    const containerRef = useRef<HTMLElement | null>(null);
    const editorRef = useRef<JSONEditor | null>(null);
    const {
      state: {selectedTheme},
    } = currentTheme;
    const [isJSONValid, setIsJSONValid] = useState(true);

    function checkIfJSONIsValid(editor: JSONEditor) {
      try {
        editor.get();
        setIsJSONValid(true);
      } catch {
        setIsJSONValid(false);
      }
    }

    useLayoutEffect(() => {
      if (editorRef.current === null && containerRef.current !== null) {
        const editor = new JSONEditor(containerRef.current, {
          mode: 'code',
          mainMenuBar: false,
          statusBar: false,
          theme:
            selectedTheme === 'dark'
              ? 'ace/theme/tomorrow_night'
              : 'ace/theme/tomorrow',
          onChange() {
            checkIfJSONIsValid(editor);
          },
        });

        if (isValidJSON(value)) {
          editor.set(JSON.parse(value));
        } else {
          editor.updateText(value);
        }

        checkIfJSONIsValid(editor);

        editorRef.current = editor;
      }

      return () => {
        editorRef.current?.destroy();
        editorRef.current = null;
      };
    }, [value, isModalVisible, selectedTheme]);

    return (
      <>
        <JSONEditorStyles />
        <Modal
          onModalClose={() => {
            onClose?.();
          }}
          isVisible={isModalVisible}
          size={SIZES.BIG}
          preventKeyboardEvents={true}
        >
          <Modal.Header>{title}</Modal.Header>
          <Body ref={containerRef} data-testid="json-editor-container" />
          <Modal.Footer>
            <Modal.SecondaryButton
              title="Close modal"
              onClick={() => {
                onClose?.();
              }}
              type="button"
            >
              Cancel
            </Modal.SecondaryButton>
            <Modal.PrimaryButton
              title="Save"
              onClick={() => {
                onSave?.(JSON.stringify(editorRef.current?.get()));
              }}
              type="button"
              disabled={!isJSONValid}
            >
              Apply
            </Modal.PrimaryButton>
          </Modal.Footer>
        </Modal>
      </>
    );
  }
);

export {JSONEditorModal};
