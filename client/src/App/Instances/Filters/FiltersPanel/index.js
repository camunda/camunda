/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {Observer} from 'mobx-react';

import {PANEL_POSITION, BADGE_TYPE} from 'modules/constants';
import {withCollapsablePanel} from 'modules/contexts/CollapsablePanelContext';

import {instances} from 'modules/stores/instances';

import CollapsablePanel from 'modules/components/CollapsablePanel';
import Badge from 'modules/components/Badge';

import * as Styled from './styled';

function Header() {
  return (
    <Styled.FiltersHeader>
      <Badge type={BADGE_TYPE.FILTERS} data-test="filter-panel-header-badge">
        <Observer>{() => instances.state.filteredInstancesCount}</Observer>
      </Badge>
    </Styled.FiltersHeader>
  );
}

export const RawFiltersPanel = (props) => {
  return (
    <CollapsablePanel
      maxWidth={328}
      label="Filters"
      header={<Header />}
      panelPosition={PANEL_POSITION.LEFT}
      isCollapsed={props.isFiltersCollapsed}
      toggle={props.toggleFilters}
      scrollable
    >
      {props.children}
    </CollapsablePanel>
  );
};

RawFiltersPanel.propTypes = {
  isFiltersCollapsed: PropTypes.bool.isRequired,
  toggleFilters: PropTypes.func.isRequired,
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node,
  ]),
};

export const FiltersPanel = withCollapsablePanel(RawFiltersPanel);
