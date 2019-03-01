import React from 'react';
import * as Styled from './styled';

function IncidentsOverlay(props) {
  return <Styled.Overlay>{props.children}</Styled.Overlay>;
}

export default IncidentsOverlay;
