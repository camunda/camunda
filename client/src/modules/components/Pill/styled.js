import styled, {css} from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';

import {ReactComponent as ClockIcon} from 'modules/components/Icon/clock.svg';

const iconStyles = css`
  margin-right: 4px;
`;

export const Clock = styled(ClockIcon)`
  ${iconStyles};
`;

const setColors = (active, dark, light) => css`
  ${({isActive}) => (isActive ? active : themeStyle({dark, light}))};
`;
const setHoverColors = (dark, light) => css`
  ${({isActive}) => !isActive && themeStyle({dark, light})};
`;

export const Pill = themed(styled.button`
  display: flex;
  align-items: center;

  border-radius: 16px;
  font-size: 13px;
  padding: 3px 10px;
  color: ${setColors('#ffffff', '#ffffff', Colors.uiDark05)};

  border-style: solid;
  border-width: 1px;
  border-color: ${setColors(
    Colors.selections,
    Colors.uiDark06,
    Colors.uiLight03
  )};

  background: ${setColors(
    Colors.selections,
    Colors.uiDark05,
    Colors.uiLight05
  )};

  &:hover {
    background: ${setHoverColors(Colors.darkPillHover, Colors.lightButton01)};
    border-color: ${setHoverColors(Colors.darkButton02, Colors.lightButton02)};
  }
`);
