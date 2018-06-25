import styled, {css} from 'styled-components';

import Panel from 'modules/components/Panel';

const isCollapsed = ({expandedId, containerId}) =>
  expandedId && expandedId !== containerId;

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
