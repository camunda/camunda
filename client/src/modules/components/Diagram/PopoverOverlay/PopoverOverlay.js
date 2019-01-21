import React from 'react';
import PropTypes from 'prop-types';

import Overlay from '../Overlay';

import * as Styled from './styled';

const position = {
  bottom: -12,
  left: -20
};

export default function PopoverOverlay(props) {
  const {
    metadata,
    onOverlayAdd,
    onOverlayClear,
    isViewerLoaded,
    selectedFlowNode,
    theme
  } = props;

  return (
    <Overlay
      onOverlayAdd={onOverlayAdd}
      onOverlayClear={onOverlayClear}
      isViewerLoaded={isViewerLoaded}
      id={selectedFlowNode}
      type={'popover'}
      position={position}
    >
      <Styled.Popover theme={theme}>
        <Styled.Metadata>
          <tbody>
            {Object.entries(metadata).map(([key, value]) => {
              return (
                <Styled.MetadataRow key={key}>
                  <td>{key}:</td>
                  <td>{JSON.stringify(value)}</td>
                </Styled.MetadataRow>
              );
            })}
          </tbody>
        </Styled.Metadata>
      </Styled.Popover>
    </Overlay>
  );
}

PopoverOverlay.propTypes = {
  metadata: PropTypes.object,
  selectedFlowNode: PropTypes.string.isRequired,
  onOverlayAdd: PropTypes.func.isRequired,
  onOverlayClear: PropTypes.func.isRequired,
  isViewerLoaded: PropTypes.bool.isRequired,
  theme: PropTypes.oneOf(['dark', 'light']).isRequired
};
