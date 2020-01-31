/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useRef, useEffect} from 'react';
import PropTypes from 'prop-types';

import {PANEL_POSITION} from 'modules/constants';
import usePrevious from 'modules/hooks/usePrevious';
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
  isCollapsed,
  toggle,
  ...props
}) {
  const buttonDirection =
    panelPosition === PANEL_POSITION.RIGHT ? DIRECTION.RIGHT : DIRECTION.LEFT;

  const expandButtonRef = useRef(null);
  const collapseButtonRef = useRef(null);

  const prevIsCollapsed = usePrevious(isCollapsed);

  useEffect(() => {
    if (prevIsCollapsed !== isCollapsed) {
      if (isCollapsed) {
        setTimeout(() => expandButtonRef.current.focus(), TRANSITION_TIMEOUT);
      } else {
        setTimeout(() => collapseButtonRef.current.focus(), TRANSITION_TIMEOUT);
      }
    }
  }, [isCollapsed, prevIsCollapsed]);

  return (
    <Styled.Collapsable
      {...props}
      isCollapsed={isCollapsed}
      panelPosition={panelPosition}
      isOverlay={isOverlay}
    >
      <Styled.CollapsedPanel
        isCollapsed={isCollapsed}
        panelPosition={panelPosition}
        transitionTimeout={TRANSITION_TIMEOUT}
        data-test="collapsed-panel"
      >
        <Styled.ExpandButton
          ref={expandButtonRef}
          title={`Expand ${label}`}
          onClick={toggle}
          panelPosition={panelPosition}
          data-test="expand-button"
        >
          <Styled.Vertical>
            <span>{label}</span>
            {renderHeader ? renderHeader() : ''}
          </Styled.Vertical>
        </Styled.ExpandButton>
      </Styled.CollapsedPanel>

      <Styled.ExpandedPanel
        isCollapsed={isCollapsed}
        panelPosition={panelPosition}
        transitionTimeout={TRANSITION_TIMEOUT}
        data-test="expanded-panel"
      >
        <Styled.Header panelPosition={panelPosition}>
          <Styled.CollapseButton
            ref={collapseButtonRef}
            direction={buttonDirection}
            isExpanded={true}
            title={`Collapse ${label}`}
            onClick={toggle}
            data-test="collapse-button"
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
  toggle: PropTypes.func.isRequired,
  isCollapsed: PropTypes.bool.isRequired,
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ])
};

export default CollapsablePanel;
