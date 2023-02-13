/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useCallback, useEffect, useState} from 'react';
import {useLexicalComposerContext} from '@lexical/react/LexicalComposerContext';
import {OnChangePlugin} from '@lexical/react/LexicalOnChangePlugin';

export default function InitialStatePlugin({value, onChange}) {
  const [editor] = useLexicalComposerContext();
  const [isFirstRender, setIsFirstRender] = useState(true);

  useEffect(() => {
    if (isFirstRender) {
      setIsFirstRender(false);

      if (value) {
        const initialEditorState = editor.parseEditorState(JSON.stringify(value));
        editor.setEditorState(initialEditorState);
      }
    }
  }, [isFirstRender, value, editor]);

  const onEditorChange = useCallback(
    (editorState) => {
      onChange?.(editorState.toJSON());
    },
    [onChange]
  );

  return <OnChangePlugin onChange={onEditorChange} ignoreSelectionChange />;
}
