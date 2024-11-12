/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import OriginalEditor, {
  useMonaco as originUseMonaco,
  DiffEditor as OriginalDiffEditor,
} from 'modules/components/MonacoEditor';
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
