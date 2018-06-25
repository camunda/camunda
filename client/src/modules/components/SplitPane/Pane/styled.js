import styled, {css} from 'styled-components';

import Panel from 'modules/components/Panel';

const expandStyle = ({expandedId, containerId}) =>
  expandedId && expandedId !== containerId ? `` : 'flex-grow: 1; height: 100%';

export const Pane = styled(Panel)`
  ${expandStyle};
`;

const hideStyle = css`
  overflow: hidden;
  ${({expandedId, containerId}) =>
    expandedId && expandedId !== containerId
      ? 'height: 0; padding:0; border:none;'
      : ''};
`;

export const Body = styled(Panel.Body)`
  ${hideStyle};
`;

export const Footer = styled(Panel.Footer)`
  ${hideStyle};
`;
