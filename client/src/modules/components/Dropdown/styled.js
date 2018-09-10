import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Dropdown = styled.div`
  position: relative;
  height: 20px;
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
  color: ${({disabled}) =>
    disabled
      ? themeStyle({
          dark: 'rgba(255, 255, 255, 0.6)',
          light: 'rgba(98, 98, 110, 0.6);'
        })
      : themeStyle({
          dark: 'rgba(255, 255, 255, 0.9)',
          light: 'rgba(98, 98, 110, 0.9)'
        })};

  background: none;

  /* Text */
  font-family: IBMPlexSans;
  font-size: 15px;
  font-weight: 600;

  /* Other */
  cursor: ${({disabled}) => (disabled ? 'default' : 'pointer')};

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
