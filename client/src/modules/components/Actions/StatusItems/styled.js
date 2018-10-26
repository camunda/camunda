import styled, {css} from 'styled-components';
import {Colors, themed} from 'modules/theme';

import {Retry, Stop} from 'modules/components/Icon';

export const Ul = themed(styled.ul`
  display: inline-flex;
  flex-direction: row;
  padding: 0 0 0 2px;
  margin: 0px;

  list-style-type: none;
  border-radius: 12px;

  background: transparent;
`);

export const Li = themed(styled.div`
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
