import styled from 'styled-components';

import Modal from 'modules/components/Modal';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Popover = styled.div`
  &:before,
  &:after {
    position: absolute;
    border: solid transparent;
    content: ' ';
    pointer-events: none;
    bottom: 50%;
    pointer-events: none;
    bottom: 100%;
  }

  &:before {
    border-width: 9px;
    border-bottom-color: ${themeStyle({
      dark: 'rgba(0, 0, 0, 0.6)',
      light: 'rgba(0, 0, 0, 0.2)'
    })};
    left: 20px;
  }

  &:after {
    border-width: 8px;
    border-bottom-color: ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight02
    })};
    left: 21px;
  }

  background-color: ${themeStyle({
    dark: Colors.uiDark04,
    light: Colors.uiLight02
  })};

  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06
  })};

  font-size: 12px;
  border-radius: 3px;

  box-shadow: 0 0 2px 0
    ${themeStyle({
      dark: 'rgba(0, 0, 0, 0.6)',
      light: 'rgba(0, 0, 0, 0.2)'
    })};

  padding: 11px;
  position: relative;
`;

export const Metadata = styled.table`
  margin: 0;
  padding: 0;
  font-weight: 600;
`;

export const MetadataRow = styled.tr`
  & td {
    white-space: nowrap;
  }
  & td:first-child {
    text-align: right;
    font-weight: normal;
    margin-right: 6px;
  }
`;

export const MoreButton = themed(styled.button`
  padding: 0;
  margin: 0;
  background: transparent;
  border: 0;
  font-size: 12px;
  text-decoration: underline;
  color: ${themeStyle({
    dark: Colors.darkLinkBlue,
    light: Colors.lightLinkBlue
  })};
`);

export const ModalBody = themed(styled(Modal.Body)`
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06
  })};

  background-color: ${themeStyle({
    dark: Colors.uiDark01,
    light: Colors.uiLight04
  })};

  position: relative;
  counter-reset: line;

  & pre {
    margin: 0;
  }
`);

export const CodeLine = themed(styled.p`
  margin: 3px;
  line-height: 14px;
  &:before {
    counter-increment: line;
    content: counter(line);
    padding-left: 18px;
    padding-right: 9px;
    opacity: 0.5;
    color: ${themeStyle({
      dark: '#ffffff',
      light: '#000000'
    })};
    font-size: 12px;
  }
`);

export const LinesSeparator = themed(styled.span`
  position: absolute;
  top: 0;
  left: 34px;
  height: 100%;
  width: 1px;
  background-color: ${themeStyle({
    dark: Colors.uiDark02,
    light: Colors.uiLight05
  })};
`);
