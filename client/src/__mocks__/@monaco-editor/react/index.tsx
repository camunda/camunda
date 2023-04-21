/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import OriginalEditor, {
  useMonaco as originUseMonaco,
  DiffEditor as OriginalDiffEditor,
} from '@monaco-editor/react';
import {editor} from 'monaco-editor';

class MockModelContentChangedEvent implements editor.IModelContentChangedEvent {
  changes = [];
  eol = '';
  versionId = 0;
  isUndoing = false;
  isRedoing = false;
  isFlush = false;
  isEolChange = false;
}

const Editor: React.FC<React.ComponentProps<typeof OriginalEditor>> = ({
  value,
  onChange,
}) => {
  return (
    <textarea
      data-testid="monaco-editor"
      value={value}
      onChange={(event) => {
        onChange?.(event.target.value, new MockModelContentChangedEvent());
      }}
    />
  );
};

const useMonaco: typeof originUseMonaco = () => {
  return null;
};

const DiffEditor: React.FC<React.ComponentProps<typeof OriginalDiffEditor>> = ({
  original,
  modified,
}) => {
  return (
    <>
      <textarea value={original} readOnly />
      <textarea value={modified} readOnly />
    </>
  );
};

export {useMonaco, DiffEditor};
export default Editor;
