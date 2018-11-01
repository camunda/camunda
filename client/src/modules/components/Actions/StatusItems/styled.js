import styled, {css} from 'styled-components';
import {Colors, themed} from 'modules/theme';

import {ReactComponent as Retry} from 'modules/components/Icon/retry.svg';
import {ReactComponent as Stop} from 'modules/components/Icon/retry.svg';

export const Ul = themed(styled.ul`
   display: flex;
  flex-direction: row;
  align-items: center;
  padding: 0 0 0 2px;
  margin: 0px;

  list-style-type: none;
  border-radius: 12px;

  background: transparent;
`);

export const Li = themed(styled.li`
  display: flex;
  align-items: center;
  padding: 3px;

  background: none;
  border: none;
  border-radius: 12px;
`);

export const iconStyle = css`
  color: ${Colors.incidentsAndErrors};
`;

// import and style any Icon accordingly
export const RetryIcon = themed(styled(Retry)`
  ${iconStyle};
`);

export const CancelIcon = themed(styled(Stop)`
  ${iconStyle};
`);
