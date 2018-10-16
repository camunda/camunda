import styled from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';

import Badge from '../Badge';

export const ComboBadge = styled.div`
  position: relative;
  height: 21px;
  min-width: 48px;
  display: flex;
  align-items: center;
  margin-left: 6px;
`;

export const Left = themed(styled(Badge)`
  z-index: 4;
  min-width: 21px;
  height: 21px;
  margin-left: 0px;
  border-style: solid;
  border-width: 2px;
  border-color: ${themeStyle({
    dark: Colors.uiDark03,
    light: Colors.uiLight02
  })};
`);

export const Right = themed(styled(Badge)`
  padding-left: 18px;
  padding-right: 10px;
  margin-left: -10px;
`);
