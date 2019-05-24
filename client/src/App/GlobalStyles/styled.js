/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

// import styled from 'styled-components';
import {interactions, themed} from 'modules/theme';
import {createGlobalStyle} from 'styled-components';

const resetFocusCss = 'outline: none';

export const GlobalStyles = themed(createGlobalStyle`
  // these elements have custom styling for :focus only on keyboard focus,
  //  not on mouse click (clicking them does not show the focus style)
  button:focus,
  code:focus,
  a:focus {
    ${({tabKeyPressed}) =>
      tabKeyPressed ? interactions.focus.css : resetFocusCss};
  }
  
  // these elements have custom styling for :focus on keyboard & mouse focus,
  // (clicking them does shows the focus style)
  input,
  textarea,
  select {
    ${interactions.focus.selector};
  }
`);
