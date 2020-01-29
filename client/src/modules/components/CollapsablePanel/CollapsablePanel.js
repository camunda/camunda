/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';
import PropTypes from 'prop-types';

import {PANEL_POSITION} from 'modules/constants';

import Panel from 'modules/components/Panel';
import * as Styled from './styled';
import {DIRECTION} from '../../constants';

const TRANSITION_TIMEOUT = 200;

function CollapsablePanel({
  label,
  panelPosition,
  renderHeader,
  renderFooter,
  isOverlay,
  children,
  ...props
}) {
  const [isCollapsed, setCollapsed] = useState(true);

  const buttonDirection =
    panelPosition === PANEL_POSITION.RIGHT ? DIRECTION.RIGHT : DIRECTION.LEFT;

  const expand = () => {
    setCollapsed(false);
  };

  const collapse = () => {
    setCollapsed(true);
  };

  return (
    <Styled.Collapsable
      {...props}
      isCollapsed={isCollapsed}
      position={panelPosition}
      isOverlay={isOverlay}
    >
      <Styled.CollapsedPanel
        isCollapsed={isCollapsed}
        transitionTimeout={TRANSITION_TIMEOUT}
      >
        <Styled.VerticalButton onClick={expand} label={label}>
          {renderHeader ? renderHeader() : ''}
        </Styled.VerticalButton>
      </Styled.CollapsedPanel>

      <Styled.ExpandedPanel
        isCollapsed={isCollapsed}
        transitionTimeout={TRANSITION_TIMEOUT}
      >
        <Styled.Header position={panelPosition}>
          <Styled.CollapseButton
            direction={buttonDirection}
            isExpanded={true}
            title="Collapse Filters"
            onClick={collapse}
          />
          {label}
          {renderHeader ? renderHeader() : ''}
        </Styled.Header>
        <Panel.Body>{children}</Panel.Body>
        <Panel.Footer>{renderFooter ? renderFooter() : ''}</Panel.Footer>
      </Styled.ExpandedPanel>
    </Styled.Collapsable>
  );
}

CollapsablePanel.propTypes = {
  label: PropTypes.string.isRequired,
  panelPosition: PropTypes.oneOf([PANEL_POSITION.RIGHT, PANEL_POSITION.LEFT])
    .isRequired,
  renderHeader: PropTypes.func,
  renderFooter: PropTypes.func,
  isOverlay: PropTypes.bool,
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ])
};

export default CollapsablePanel;
