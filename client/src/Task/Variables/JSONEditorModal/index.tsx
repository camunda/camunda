/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Modal} from './Modal';
import {useLayoutEffect, useRef, useState} from 'react';
import JSONEditor from 'jsoneditor';
import 'jsoneditor/dist/jsoneditor.css';
import {GlobalStyles} from './styled';
import 'brace/theme/tomorrow';
import {isValidJSON} from 'modules/utils/isValidJSON';
type Props = {
  onClose?: () => void;
  onSave?: (value: string | undefined) => void;
  value: string | undefined;
  title?: string;
};

const JSONEditorModal: React.FC<Props> = ({
  onClose,
  onSave,
  value = '',
  title,
}) => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const editorRef = useRef<JSONEditor | null>(null);
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
        theme: 'ace/theme/tomorrow',
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
  }, [value]);

  return (
    <>
      <GlobalStyles />
      <Modal
        title={title}
        onClose={onClose}
        onSave={() => {
          onSave?.(JSON.stringify(editorRef.current?.get()));
        }}
        isSaveDisabled={!isJSONValid}
      >
        <div ref={containerRef} />
      </Modal>
    </>
  );
};

export {JSONEditorModal};
