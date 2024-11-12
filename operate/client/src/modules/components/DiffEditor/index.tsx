/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  useMonaco,
  DiffEditor as BaseDiffEditor,
} from 'modules/components/MonacoEditor';
import {observer} from 'mobx-react-lite';
import {currentTheme} from 'modules/stores/currentTheme';
import {options} from 'modules/utils/editor/options';
import {EditorStyles} from './styled';

type Props = {
  originalValue: string;
  modifiedValue: string;
  height?: string;
  width?: string;
};

const DiffEditor: React.FC<Props> = observer(
  ({originalValue, modifiedValue, height = '90vh', width = '100%'}) => {
    return (
      <>
        <EditorStyles />
        <BaseDiffEditor
          height={height}
          width={width}
          original={originalValue}
          modified={modifiedValue}
          theme={currentTheme.theme === 'dark' ? 'vs-dark' : 'light'}
          language="json"
          options={{...options, readOnly: true, renderOverviewRuler: false}}
        />
      </>
    );
  },
);

export {DiffEditor, useMonaco};
