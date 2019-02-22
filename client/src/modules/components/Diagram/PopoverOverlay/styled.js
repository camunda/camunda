import styled, {css} from 'styled-components';
import {POPOVER_SIDE} from 'modules/constants';

import Modal from 'modules/components/Modal';
import {Colors, themed, themeStyle} from 'modules/theme';

const arrowStyle = ({side}) => {
  switch (side) {
    case POPOVER_SIDE.BOTTOM:
      return css`
        &:before,
        &:after {
          position: absolute;
          content: ' ';
          pointer-events: none;
          color: transparent;
          border-style: solid;
          border-width: 9px;
          bottom: 100%;
        }

        &:before {
          border-bottom-color: ${themeStyle({
            dark: Colors.uiDark06,
            light: Colors.uiLight05
          })};
          left: calc(50% - 9px);
        }

        &:after {
          border-bottom-color: ${themeStyle({
            dark: Colors.uiDark04,
            light: Colors.uiLight02
          })};
          left: calc(50% - 8px);
        }
      `;

    case POPOVER_SIDE.LEFT:
      return css`
        &:before,
        &:after {
          position: absolute;
          content: ' ';
          pointer-events: none;
          color: transparent;
          border-style: solid;
          border-width: 9px;
          left: 100%;
        }

        &:before {
          border-left-color: ${themeStyle({
            dark: Colors.uiDark06,
            light: Colors.uiLight05
          })};
          top: calc(50% - 9px);
        }

        &:after {
          border-left-color: ${themeStyle({
            dark: Colors.uiDark04,
            light: Colors.uiLight02
          })};
          top: calc(50% - 8px);
        }
      `;

    case POPOVER_SIDE.RIGHT:
      return css`
        &:before,
        &:after {
          position: absolute;
          content: ' ';
          pointer-events: none;
          color: transparent;
          border-style: solid;
          border-width: 9px;
          right: 100%;
        }

        &:before {
          border-right-color: ${themeStyle({
            dark: Colors.uiDark06,
            light: Colors.uiLight05
          })};
          top: calc(50% - 9px);
        }

        &:after {
          border-right-color: ${themeStyle({
            dark: Colors.uiDark04,
            light: Colors.uiLight02
          })};
          top: calc(50% - 8px);
        }
      `;

    default:
      return css`
        &:before,
        &:after {
          position: absolute;
          content: ' ';
          pointer-events: none;
          color: transparent;
          border-style: solid;
          border-width: 9px;
          top: 100%;
        }

        &:before {
          border-top-color: ${themeStyle({
            dark: Colors.uiDark06,
            light: Colors.uiLight05
          })};
          left: calc(50% - 9px);
        }

        &:after {
          border-top-color: ${themeStyle({
            dark: Colors.uiDark04,
            light: Colors.uiLight02
          })};
          left: calc(50% - 8px);
        }
      `;
  }
};

const transformStyle = ({side}) => {
  if (side === 'RIGHT') {
    return css`
      transform: translate(0, -50%);
    `;
  }

  if (side === 'BOTTOM') {
    return css`
      transform: translate(-50%, 0);
    `;
  }

  if (side === 'LEFT') {
    return css`
      transform: translate(-100%, -50%);
    `;
  }

  return css`
    transform: translate(-50%, -100%);
  `;
};

export const Popover = styled.div`
  ${arrowStyle}

  background-color: ${themeStyle({
    dark: Colors.uiDark04,
    light: Colors.uiLight02
  })};

  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06
  })};

  font-size: 12px;
  border: 1px solid
    ${themeStyle({
      dark: Colors.uiDark06,
      light: Colors.uiLight05
    })};

  border-radius: 3px;

  box-shadow: 0 0 2px 0
    ${themeStyle({
      dark: 'rgba(0, 0, 0, 0.6)',
      light: 'rgba(0, 0, 0, 0.2)'
    })};

  padding: 11px;
  padding-top: 12px;

  ${transformStyle}
`;

export const PeterCaseSummaryHeader = styled.div`
  font-weight: bold;
  text-align: center;
  white-space: nowrap;
`;

export const PeterCaseSummaryBody = styled.div`
  margin-top: 3px;
  text-align: center;
  width: 293px;
`;

export const SummaryHeader = styled.div`
  margin-bottom: 12px;
  white-space: nowrap;
`;

export const SummaryDataCell = styled.div`
  white-space: nowrap;
`;

export const SummaryData = styled.div`
  margin: 0;
  padding: 0;
  font-weight: 600;
  display: flex;
  flex-direction: column;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  grid-column-gap: 6px;

  ${SummaryDataCell}:nth-child(odd) {
    text-align: right;
    font-weight: normal;
  }
`;

export const Button = themed(styled.button`
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
  font-family: IBMPlexMono;

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
    font-size: 14px;
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
