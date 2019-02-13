import styled, {css} from 'styled-components';

import {themed, themeStyle, Colors} from 'modules/theme';
import {ReactComponent as Down} from 'modules/components/Icon/down.svg';
import {ReactComponent as Right} from 'modules/components/Icon/right.svg';
import withStrippedProps from 'modules/utils/withStrippedProps';

const iconStyle = css`
  width: 16px;
  height: 16px;
  object-fit: contain;
  color: ${({theme}) => {
    return theme === 'dark' ? 'rgba(255, 255, 255, 0.9)' : Colors.uiDark04;
  }};
`;

export const FoldButton = themed(styled.button`
  background: transparent;
  border-radius: 50%;
  border-color: none;
  padding: 0;
  height: 16px;
  width: 16px;
  position: absolute;
  left: -24px;
  top: 6px;
  z-index: 2;

  &:hover {
    background: ${themeStyle({
      dark: 'rgba(255, 255, 255, 0.25)',
      light: 'rgba(216, 220, 227, 0.5)'
    })};
  }

  &:active {
    background: ${themeStyle({
      dark: 'rgba(255, 255, 255, 0.4)',
      light: 'rgba(216, 220, 227, 0.8)'
    })};
  }
`);

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
  height: 28px;
`);

const selectionStyles = css`
  border-color: ${Colors.primaryButton01};
  background: ${Colors.selections};
  color: '#fff';
`;

export const SummaryLabel = themed(styled.div`
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  margin: 0;
  padding: 0;
  border: none;
  font-size: 14px;
  text-align: left;
  background: ${themeStyle({
    dark: Colors.darkItemEven,
    light: Colors.lightItemEven
  })};

  ${({isSelected}) => isSelected && selectionStyles};
`);

export const FocusButton = themed(styled.button`
  position: absolute;
  background: transparent;
  left: 1px;
  top: 1px;
  height: calc(100% - 4px);
  z-index: 1;
  width: calc(100% - 5px);

  /* Apply hover styles to sibling (SummaryLabel)*/
  &:hover + div {
    background: ${({isSelected}) =>
      !isSelected &&
      themeStyle({
        dark: Colors.uiDark04,
        light: Colors.lightButton05
      })};
  }
`);

export const Details = themed(styled.div`
  display: ${({isFolded}) => (isFolded ? 'none' : 'block')};
`);
