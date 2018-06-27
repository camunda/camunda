import styled, {css} from 'styled-components';

import Panel from 'modules/components/Panel';
import ExpandButton from 'modules/components/ExpandButton';

const isCollapsed = ({expandedId, paneId}) =>
  expandedId && expandedId !== paneId;

const nonCollapsedPaneStyle = css`
  flex-grow: 1;
  height: 100%;
`;

export const Pane = styled(Panel)`
  ${props => (isCollapsed(props) ? '' : nonCollapsedPaneStyle)};
`;

const withCollapsableStyle = target => {
  const collapsedStyle = css`
    height: 0;
    padding: 0;
    border: none;
  `;

  return styled(target)`
    overflow: hidden;
    ${props => (isCollapsed(props) ? collapsedStyle : '')};
  `;
};

export const Body = withCollapsableStyle(Panel.Body);

export const Footer = withCollapsableStyle(Panel.Footer);

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
  ${props => (isCollapsed(props) ? buttonInHeaderBorder : buttonInBodyBorder)};
`;

export const BottomExpandButton = styled(ExpandButton)`
  position: absolute;
  top: 0;
  right: 0;
  ${buttonInHeaderBorder};
`;
