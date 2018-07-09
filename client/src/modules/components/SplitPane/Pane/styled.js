import styled, {css} from 'styled-components';

import Panel from 'modules/components/Panel';
import ExpandButton from 'modules/components/ExpandButton';
import {EXPAND_STATE} from 'modules/constants';

const isCollapsed = expandState => expandState === EXPAND_STATE.COLLAPSED;

const nonCollapsedPaneStyle = css`
  flex-grow: 1;
  height: 100%;
`;

export const Pane = styled(Panel)`
  ${({expandState}) => (isCollapsed(expandState) ? '' : nonCollapsedPaneStyle)};
`;

const collapsedStyle = css`
  overflow: hidden;
  height: 0;
  padding: 0;
  border: none;
`;

export const Body = styled(Panel.Body)`
  ${({expandState}) => (isCollapsed(expandState) ? collapsedStyle : '')};
`;

export const Footer = styled(Panel.Footer)`
  ${({expandState}) => (isCollapsed(expandState) ? collapsedStyle : '')};
`;

const buttonInBodyBorder = css`
  border-bottom: none;
  border-right: none;
`;

const buttonInHeaderBorder = css`
  border-top: none;
  border-bottom: none;
  border-right: none;
`;

export const TopExpandButton = styled(ExpandButton)`
  position: absolute;
  right: 0;
  bottom: 0;
  ${({expandState}) =>
    isCollapsed(expandState) ? buttonInHeaderBorder : buttonInBodyBorder};
`;

export const BottomExpandButton = styled(ExpandButton)`
  position: absolute;
  top: 0;
  right: 0;
  ${buttonInHeaderBorder};
`;
