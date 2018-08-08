import styled, {css} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';
import {DROPDOWN_PLACEMENT} from 'modules/constants';

import {PointerBasics, PointerBody} from '../styled';

const bottomPointer = css`
&:first-child:hover:after, &:first-child:hover:before {
  ${PointerBasics};
  }

  &:first-child:hover:after, &:first-child:hover:before {
  ${PointerBasics};
  }

  &:first-child:hover:after{
    z-index: 1;
    ${PointerBody}
      border-bottom-color: ${themeStyle({
        dark: Colors.uiDark06,
        light: Colors.uiLight05
      })};
  }

  &:first-child:active:after{
      ${PointerBody}
      border-bottom-color: ${themeStyle({
        dark: Colors.darkActive,
        light: Colors.lightActive
      })};
  }
`;

const topPointer = css`
&:last-child:hover:after, &:last-child:hover:before {
${PointerBasics};
}

&:last-child:hover:after, &:last-child:hover:before {
${PointerBasics};
}

&:last-child:hover:after{
  z-index: 1;
  ${PointerBody}
    border-bottom-color: ${themeStyle({
      dark: Colors.uiDark06,
      light: Colors.uiLight05
    })};
}

&:last-child:active:after{
    ${PointerBody}
    border-bottom-color: ${themeStyle({
      dark: Colors.darkActive,
      light: Colors.lightActive
    })};
}
`;

export const Option = themed(styled.button`
  /* Positioning */
  height: 36px;

  /* Display & Box Model */
  display:flex;
  align-items: center;
  width: 100%;
  padding: 0 10px;
  border: none;
  outline: none;

  /* Color */
  color: currentColor;
  background: none;
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: 'rgba(98, 98, 110, 0.9)'
  })};
  /* Text */
  text-align: left;
  font-size: 15px;
  font-weight: 600;
  line-height: 36px;

  /* Other */
  cursor: pointer;


  &:hover {
    background: ${themeStyle({
      dark: Colors.uiDark06,
      light: Colors.uiLight05
    })};
  }

  &:active {
    background: ${themeStyle({
      dark: Colors.darkActive,
      light: Colors.lightActive
    })};
  }

  /* DropDown pointer direction styles */
  ${({placement}) =>
    placement === DROPDOWN_PLACEMENT.TOP ? topPointer : bottomPointer};


  /* Add Border between options */
  &:not(:last-child) {
    border-bottom: 1px solid ${themeStyle({
      dark: Colors.uiDark06,
      light: Colors.uiLight05
    })}
`);
