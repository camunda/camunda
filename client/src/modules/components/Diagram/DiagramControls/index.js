/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {ReactComponent as DiagramReset} from 'modules/components/Icon/diagram-reset.svg';
import {ReactComponent as Plus} from 'modules/components/Icon/plus.svg';
import {ReactComponent as Minus} from 'modules/components/Icon/minus.svg';

import * as Styled from './styled';

export default function DiagramControls({
  handleZoomReset,
  handleZoomIn,
  handleZoomOut,
}) {
  return (
    <Styled.DiagramControls>
      <Styled.ZoomReset onClick={handleZoomReset}>
        <DiagramReset />
      </Styled.ZoomReset>
      <Styled.ZoomIn onClick={handleZoomIn}>
        <Plus />
      </Styled.ZoomIn>
      <Styled.ZoomOut onClick={handleZoomOut}>
        <Minus />
      </Styled.ZoomOut>
    </Styled.DiagramControls>
  );
}

DiagramControls.propTypes = {
  handleZoomReset: PropTypes.func.isRequired,
  handleZoomIn: PropTypes.func.isRequired,
  handleZoomOut: PropTypes.func.isRequired,
};
