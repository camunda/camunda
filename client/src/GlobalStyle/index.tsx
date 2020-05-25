/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import * as React from 'react';

import * as Styled from './styled';
import {USING_KEYBOARD_CLASS_NAME} from './constans';

const TAB_KEY_CODE = 9;

const GlobalStyle: React.FC = () => {
  React.useEffect(() => {
    function onMouseDown() {
      document.body.classList.remove(USING_KEYBOARD_CLASS_NAME);
    }

    function onKeyDown(event: KeyboardEvent) {
      if (event.keyCode === TAB_KEY_CODE) {
        document.body.classList.add(USING_KEYBOARD_CLASS_NAME);
      }
    }

    document.body.addEventListener('mousedown', onMouseDown);
    document.body.addEventListener('keydown', onKeyDown);

    return () => {
      document.body.removeEventListener('mousedown', onMouseDown);
      document.body.removeEventListener('keydown', onKeyDown);
    };
  }, []);

  return <Styled.GlobalStyle />;
};

export {GlobalStyle};
