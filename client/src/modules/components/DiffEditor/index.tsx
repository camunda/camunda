/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useMonaco, DiffEditor as BaseDiffEditor} from '@monaco-editor/react';
import {observer} from 'mobx-react-lite';
import {currentTheme} from 'modules/stores/currentTheme';
import {options} from 'modules/utils/editor/options';
import {EditorStyles} from './styled';

type Props = {
  originalValue: string;
  modifiedValue: string;
};

const DiffEditor: React.FC<Props> = observer(
  ({originalValue, modifiedValue}) => {
    const {
      state: {selectedTheme},
    } = currentTheme;

    return (
      <>
        <EditorStyles />
        <BaseDiffEditor
          height="90vh"
          original={originalValue}
          modified={modifiedValue}
          theme={selectedTheme === 'dark' ? 'vs-dark' : 'light'}
          language="json"
          options={{...options, readOnly: true, renderOverviewRuler: false}}
        />
      </>
    );
  }
);

export {DiffEditor, useMonaco};
