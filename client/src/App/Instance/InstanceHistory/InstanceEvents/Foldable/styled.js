import styled, {css} from 'styled-components';

import {themed, Colors} from 'modules/theme';
import {Down, Right} from 'modules/components/Icon';
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
  cursor: pointer;
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

export const Summary = themed(styled.div`
  position: relative;
  height: ${({isFirst}) => (isFirst ? '32px' : '31px')};
  flex: 1;
`);

export const SummaryLabel = themed(styled.button`
  cursor: pointer;
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
  background-color: ${({isSelected}) =>
    !isSelected ? 'transparent' : Colors.selections};
  font-size: 14px;
  text-align: left;
  color: ${({theme, isSelected}) => {
    return isSelected || theme === 'dark'
      ? 'rgba(255, 255, 255, 0.9)'
      : Colors.uiDark04;
  }};
`);

export const Details = styled.div`
  display: ${({isFolded}) => (!isFolded ? 'block' : 'none')};
`;
