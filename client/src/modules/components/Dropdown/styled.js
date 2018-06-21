import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Label = styled.button`
  background: none;
  border: none;
  color: currentColor;
  outline: none;
  font-family: IBMPlexSans;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;

  svg {
    margin-left: 8px;
    vertical-align: text-bottom;
  }
`;

export const Dropdown = styled.div`
  display: inline-block;
  position: relative;
`;

export const DropdownMenu = themed(styled.div`
  background-color: ${themeStyle({
    dark: Colors.uiDark04,
    light: Colors.uiLight02
  })};
  border: 1px solid
    ${themeStyle({dark: Colors.uiDark06, light: Colors.uiLight05})};
  border-radius: 3px;
  box-shadow: 0 2px 2px 0
    ${themeStyle({
      dark: 'rgba(0, 0, 0, 0.5)',
      light: 'rgba(231, 233, 238, 0.5)'
    })};
  min-width: 186px;
  position: absolute;
  right: 0;
  margin-top: 5px;

  &:after,
  &:before {
    bottom: 100%;
    right: 15px;
    border: solid transparent;
    content: ' ';
    position: absolute;
    pointer-events: none;
  }

  &:after {
    border-bottom-color: ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight02
    })};
    border-width: 7px;
    margin-right: -7px;
  }
  &:before {
    border-bottom-color: ${themeStyle({
      dark: Colors.uiDark06,
      light: Colors.uiLight05
    })};
    border-width: 8px;
    margin-right: -8px;
  }
`);

export const Option = themed(styled.button`
  height: 36px;
  background: none;
  border: none;
  color: currentColor;
  outline: none;
  cursor: pointer;
  width: 100%;
  text-align: left;
  font-size: 15px;
  font-weight: 600;
  line-height: 36px;
  padding: 0 10px;

  &:not(:last-child) {
    border-bottom: 1px solid ${themeStyle({
      dark: Colors.uiDark06,
      light: Colors.uiLight05
    })}
`);
