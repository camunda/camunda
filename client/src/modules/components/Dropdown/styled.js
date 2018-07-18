import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Label = styled.button`
  display: flex;
  align-items: center;

  border: none;
  outline: none;

  color: currentColor;
  background: none;

  font-family: IBMPlexSans;
  font-size: 15px;
  font-weight: 600;

  cursor: pointer;

  svg {
    vertical-align: text-bottom;
  }
`;

export const LabelWrapper = styled.div`
  margin-right: 8px;
`;

export const Dropdown = styled.div`
  display: inline-block;
  position: relative;
`;

export const DropdownMenu = themed(styled.div`
  /* Positioning */
  position: absolute;
  right: -1px;
  top: 17px;

  /* Display & Box Model */
  min-width: 186px;
  margin-top: 5px;
  box-shadow: 0 2px 2px 0
    ${themeStyle({
      dark: 'rgba(0, 0, 0, 0.5)',
      light: 'rgba(231, 233, 238, 0.5)'
    })};
  border: 1px solid
    ${themeStyle({dark: Colors.uiDark06, light: Colors.uiLight05})};
  border-radius: 3px;

  /* Color */
  background-color: ${themeStyle({
    dark: Colors.uiDark04,
    light: Colors.uiLight02
  })};
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06
  })};

  /* Other */
  &:after,
  &:before {
    position: absolute;
    bottom: 100%;
    right: 15px;
    border: solid transparent;
    content: ' ';
    pointer-events: none;
  }

  &:after {
    border-width: 7px;
    margin-right: -7px;
    border-bottom-color: ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight02
    })};
  }
  &:before {
    border-width: 8px;
    margin-right: -8px;
    border-bottom-color: ${themeStyle({
      dark: Colors.uiDark06,
      light: Colors.uiLight05
    })};
  }

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
`);

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

  /* Text */
  text-align: left;
  font-size: 15px;
  font-weight: 600;
  line-height: 36px;

  /* Other */
  cursor: pointer;

  &:not(:last-child) {
    border-bottom: 1px solid ${themeStyle({
      dark: Colors.uiDark06,
      light: Colors.uiLight05
    })}
`);
