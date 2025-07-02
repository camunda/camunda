/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react-lite';
import Editor from '@monaco-editor/react';
import {themeStore} from 'common/theme/theme';
import styles from './styles.module.scss';

type Props = {
  value: string;
  height?: string;
  'data-testid'?: string;
};

const options = {
  minimap: {
    enabled: false,
  },
  fontSize: 13,
  lineHeight: 20,
  fontFamily:
    '"IBM Plex Mono", "Droid Sans Mono", "monospace", monospace, "Droid Sans Fallback"',
  readOnly: true,
  formatOnPaste: true,
  formatOnType: true,
  tabSize: 2,
  wordWrap: 'on' as const,
  scrollBeyondLastLine: false,
  contextmenu: false,
  lineNumbers: 'off' as const,
} as const;

function formatJSON(value: string): string {
  try {
    const parsedValue = JSON.parse(value);
    return JSON.stringify(parsedValue, null, 2);
  } catch {
    return value;
  }
}

const JSONViewer: React.FC<Props> = observer(({value, height = '300px', ...props}) => {
  const formattedValue = formatJSON(value);

  return (
    <div className={styles.container} data-testid={props['data-testid']}>
      <Editor
        className={styles.editor}
        options={options}
        language="json"
        value={formattedValue}
        height={height}
        theme={themeStore.actualTheme === 'light' ? 'light' : 'vs-dark'}
        onMount={(_, monaco) => {
          monaco?.languages.json.jsonDefaults.setDiagnosticsOptions({
            schemaValidation: 'error',
            schemaRequest: 'error',
          });
        }}
      />
    </div>
  );
});

export {JSONViewer};