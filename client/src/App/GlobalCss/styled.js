import styled from 'styled-components';
import {interactions, themed} from 'modules/theme';

const resetFocusCss = 'outline: none';

export const GlobalCss = themed(styled.div`
  // these elements have custom styling for :focus only on keyboard focus,
  //  not on mouse click (clicking them does not show the focus style)
  button:focus,
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
