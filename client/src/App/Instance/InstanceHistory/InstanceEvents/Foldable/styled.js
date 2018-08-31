import styled, {css} from 'styled-components';

import {themed, themeStyle, Colors} from 'modules/theme';
import {Down, Right} from 'modules/components/Icon';

export const Foldable = themed(styled.div`
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: Colors.uiDark04
  })};
  font-size: 14px;
`);

const iconStyle = css`
  width: 16px;
  height: 16px;
  object-fit: contain;
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: Colors.uiDark04
  })};
`;

export const FoldButton = styled.button`
  cursor: pointer;
  background: transparent;
  border: none;

  padding: 0;
  top: 1px;
`;

export const DownIcon = themed(styled(Down)`
  ${iconStyle};
`);

export const RightIcon = themed(styled(Right)`
  ${iconStyle};
`);

export const Summary = themed(styled.div`
  display: flex;
  width: 100%;
  position: relative;
  padding-top: 7px;
  padding-bottom: 7px;
  color: ${themeStyle({
    light: 'rgba(69, 70, 78, 0.9)'
  })};
`);

export const SummaryLabel = styled.span`
  margin-left: ${({isFoldable}) => (!!isFoldable ? '6px' : '22px')};
`;

export const Details = styled.div`
  padding-left: 22px;
  display: ${({isFolded}) => (!isFolded ? 'block' : 'none')};
`;
