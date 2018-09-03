import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Dropdown = styled.div`
  position: relative;
`;

export const Button = themed(styled.button`
  /* Positioning */
  position: relative;
  display: flex;
  align-items: center;

  /* Display & Box Model */
  border: none;
  outline: none;

  /* Color */
  color: currentColor;
  background: none;

  /* Text */
  font-family: IBMPlexSans;
  font-size: 15px;
  font-weight: 600;

  /* Other */
  cursor: pointer;
  &:focus {
    box-shadow: ${themeStyle({
      dark: `0 0 0 1px ${Colors.focusOuter},0 0 0 4px ${Colors.darkFocusInner}`,
      light: `0 0 0 1px ${Colors.focusOuter}, 0 0 0 4px ${
        Colors.lightFocusInner
      }`
    })};
  }

  & > svg {
    vertical-align: text-bottom;
  }
`);

export const LabelWrapper = styled.div`
  margin-right: 8px;
`;
