/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import JSONEditor from 'jsoneditor';
import {observer} from 'mobx-react-lite';
import {currentTheme} from 'modules/stores/currentTheme';
import {useLayoutEffect, useRef} from 'react';
import {Container, JSONEditorStyles} from './styled';

type Props = {
  value: string;
  'data-testid'?: string;
};

const JSONViewer: React.FC<Props> = observer(({value, ...props}) => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const {
    state: {selectedTheme},
  } = currentTheme;

  useLayoutEffect(() => {
    let editor: JSONEditor | null = null;

    if (containerRef.current !== null) {
      editor = new JSONEditor(
        containerRef.current,
        {
          mode: 'code',
          mainMenuBar: false,
          statusBar: false,
          theme:
            selectedTheme === 'dark'
              ? 'ace/theme/tomorrow_night'
              : 'ace/theme/tomorrow',
          onChange() {
            editor?.set(JSON.parse(value));
          },
        },
        JSON.parse(value)
      );
    }

    return () => {
      editor?.destroy();
    };
  }, [selectedTheme, value]);

  return (
    <>
      <JSONEditorStyles />
      <Container ref={containerRef} data-testid={props['data-testid']} />
    </>
  );
});

export {JSONViewer};
