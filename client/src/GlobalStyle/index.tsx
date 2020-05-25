/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import * as Styled from './styled';
import React, {useState, useEffect} from 'react';

const TAB_KEY_CODE = 9;
const GlobalStyle: React.FC = () => {
  const [isTabKeyPressed, setIsTabKeyPressed] = useState(false);

  const onKeyPressed = (event: KeyboardEvent) => {
    if (event.keyCode === TAB_KEY_CODE) {
      setIsTabKeyPressed(true);
    }
  };

  const onMousePressed = () => {
    setIsTabKeyPressed(false);
  };

  useEffect(() => {
    document.body.addEventListener('keydown', onKeyPressed, true);
    document.body.addEventListener('mousedown', onMousePressed, true);

    return () => {
      document.body.removeEventListener('keydown', onKeyPressed, true);
      document.body.removeEventListener('mousedown', onMousePressed, true);
    };
  }, []);

  return <Styled.GlobalStyle isTabKeyPressed={isTabKeyPressed} />;
};

export {GlobalStyle};
