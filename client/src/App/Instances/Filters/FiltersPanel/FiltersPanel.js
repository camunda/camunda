/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {DIRECTION, BADGE_TYPE} from 'modules/constants';
import {withCountStore} from 'modules/contexts/CountContext';
import {withCollapsablePanel} from 'modules/contexts/CollapsablePanelContext';

import CollapsablePanel from 'modules/components/CollapsablePanel';
import Badge from 'modules/components/Badge';

import * as Styled from './styled';

function FiltersPanel(props) {
  function renderCount() {
    return props.countStore.isLoaded ? props.countStore.filterCount : '';
  }
  return (
    <CollapsablePanel
      isCollapsed={props.isFiltersCollapsed}
      onCollapse={props.toggleFilters}
      maxWidth={328}
      expandButton={
        <Styled.VerticalButton label="Filters">
          <Badge type={BADGE_TYPE.FILTERS}>{renderCount()}</Badge>
        </Styled.VerticalButton>
      }
      collapseButton={
        <Styled.CollapseButton
          direction={DIRECTION.LEFT}
          isExpanded={true}
          title="Collapse Filters"
        />
      }
    >
      <Styled.FiltersHeader>
        Filters
        <Badge type={BADGE_TYPE.FILTERS}>{renderCount()}</Badge>
      </Styled.FiltersHeader>
      <Styled.FiltersBody>{props.children}</Styled.FiltersBody>
    </CollapsablePanel>
  );
}

FiltersPanel.propTypes = {
  isFiltersCollapsed: PropTypes.bool.isRequired,
  toggleFilters: PropTypes.func.isRequired,
  countStore: PropTypes.shape({
    running: PropTypes.number,
    active: PropTypes.number,
    withIncidents: PropTypes.number,
    filterCount: PropTypes.number,
    instancesInSelectionsCount: PropTypes.number,
    selectionCount: PropTypes.number,
    isLoaded: PropTypes.bool
  }).isRequired,
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ])
};

export default withCollapsablePanel(withCountStore(FiltersPanel));
