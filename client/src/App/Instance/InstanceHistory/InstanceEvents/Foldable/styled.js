import styled, {css} from 'styled-components';

import {themed, themeStyle, Colors} from 'modules/theme';
import {ReactComponent as Down} from 'modules/components/Icon/down.svg';
import {ReactComponent as Right} from 'modules/components/Icon/right.svg';
import withStrippedProps from 'modules/utils/withStrippedProps';

const iconStyle = css`
  width: 16px;
  height: 16px;
  object-fit: contain;
  color: ${({theme, isSelected}) => {
    return isSelected || theme === 'dark'
      ? 'rgba(255, 255, 255, 0.9)'
      : Colors.uiDark04;
  }};
`;

export const FoldButton = styled.button`
  background: transparent;
  border: none;

  padding: 0;
  top: 1px;

  position: absolute;
  left: ${({indentation}) => `${indentation * 22 + 15}px`};
  top: 8px;
  z-index: 2;
`;

export const DownIcon = themed(styled(withStrippedProps(['isSelected'])(Down))`
  ${iconStyle};
`);

export const RightIcon = themed(styled(
  withStrippedProps(['isSelected'])(Right)
)`
  ${iconStyle};
`);

const borderBottomStyle = css`
1px solid ${themeStyle({
  dark: Colors.uiDark04,
  light: Colors.uiLight05
})}`;

export const Summary = themed(styled.div`
  position: relative;
  height: 32px;
  flex: 1;
  border-bottom: ${props => (!props.isFolded ? 'none' : borderBottomStyle)};
  border-color: ${props => (!props.isSelected ? '' : Colors.selections)};
`);

export const SummaryLabel = themed(styled.button`
  padding-left: ${({indentation}) => `${indentation * 22 + 37}px`};
  font-weight: ${({isFoldable, isSelected}) =>
    isSelected || isFoldable ? 'bold' : 'normal'};
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  margin: 0;
  border: none;
  font-size: 14px;
  text-align: left;
  background-color: ${props =>
    !props.isSelected ? 'transparent' : Colors.selections};
  color: ${({theme, isSelected}) => {
    return isSelected || theme === 'dark'
      ? 'rgba(255, 255, 255, 0.9)'
      : Colors.uiDark04;
  }};
`);

export const Details = themed(styled.div`
  display: ${({isFolded}) => (!isFolded ? 'block' : 'none')};
  border-bottom: ${borderBottomStyle};
`);
