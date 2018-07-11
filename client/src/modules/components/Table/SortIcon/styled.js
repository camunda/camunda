import styled, {css} from 'styled-components';

import {Up as DefaultUp, Down as DefaultDown} from 'modules/components/Icon';
import {themed, Colors, themeStyle} from 'modules/theme';
import withStrippedProps from 'modules/utils/withStrippedProps';

export const SortIcon = styled.a``;

const inactiveStyle = css`
  ${({sortOrder}) => (!sortOrder ? 'opacity: 0.4' : '')};
`;

const sortIconStyle = css`
  height: 16px;
  width: 16px;

  ${themeStyle({
    dark: '#ffffff',
    ligth: Colors.uiLight06
  })};

  ${inactiveStyle};

  cursor: pointer;
`;

export const Up = themed(styled(withStrippedProps(['sortOrder'])(DefaultUp))`
  ${sortIconStyle};
`);

export const Down = themed(styled(
  withStrippedProps(['sortOrder'])(DefaultDown)
)`
  ${sortIconStyle};
`);
