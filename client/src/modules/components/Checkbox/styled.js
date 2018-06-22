import styled from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';

export const Checkbox = styled.div`
  position: relative;
`;

export const Label = themed(styled.label`
  /* Display & Box Model */
  display: inline-block;
  margin: 4px 0;
  margin-left: 11px;

  /* Text */
  font-size: 13px;

  /* Colors */
  opacity: 0.9;
  color: ${themeStyle({
    dark: '#c5c5c7',
    light: Colors.uiLight06
  })};
`);

export const Input = themed(styled.input`
  /* hide default checkbox */
  position: absolute;
  opacity: 0;

  /* default: empty checkbox */
  + div:after {
    content: none;
  }
  /* show checkmark*/
  &:checked + div:after {
    content: '';
  }

  /* background of selection checkbox */
  &:checked + div:before {
    background-color: ${({checkboxType}) =>
      checkboxType === 'selection' && Colors.selections};
  }

  /* add boxshadow to checked boxes*/
  &:checked + div:before {
    box-shadow: 0 2px 2px 0
      ${themeStyle({
        dark: 'rgba(0, 0, 0, 0.5)',
        light: ({checkboxType}) =>
          checkboxType === 'selection'
            ? 'rgba(0, 0, 0, 0.5)'
            : 'rgba(231, 233, 238, 0.35)'
      })};
  }

  /* simulate focus */
  &:focus + div:before {
    outline: rgb(59, 153, 252) auto 4px;
  }

  /* show indeterminate*/
  &:indeterminate + div:after {
    content: '';
  }
`);

export const CustomCheckbox = themed(styled.div`
  position: relative;
  display: inline-block;
  width: 14px;
  height: 14px;
  
  /* box styles */
  &:before {
    content: '';
    position: absolute;
    top: 3px;
    display: inline-block;
    width: 100%;
    height: 100%;
    border-radius: 3px;
    border: solid 1px ${themeStyle({
      dark: '#bebec0',
      light: Colors.uiLight03
    })};
    background: ${themeStyle({
      dark: Colors.uiDark02,
      light: Colors.uiLight01
    })};
  }
  /* checkmark/indeterminate styles*/
  &:after {
    content: '';    
    position: absolute;
    border-style: solid;
    ${({isIndeterminate}) =>
      isIndeterminate ? indeterminateStyles : checkMarkStyles};
    border-color: ${themeStyle({
      dark: '#ffffff',
      light: ({checkboxType}) =>
        checkboxType === 'selection' ? '#ffffff' : Colors.uiLight06
    })};
    };
  }
`);

const indeterminateStyles = `
left: 5px;
top: 8px;
width: 6px;
height: 2px;
border-width: 0 0 2px 0;
`;

const checkMarkStyles = `
left: 3px;
top: 7px;
height:4px;
width: 9px;
border-width: 0 0 2px 2px;
transform: rotate(-50deg);
`;
