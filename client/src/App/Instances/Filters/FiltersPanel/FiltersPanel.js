/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {PANEL_POSITION, BADGE_TYPE} from 'modules/constants';
import {withCountStore} from 'modules/contexts/CountContext';
import {withCollapsablePanel} from 'modules/contexts/CollapsablePanelContext';

import CollapsablePanel from 'modules/components/CollapsablePanel';
import Badge from 'modules/components/Badge';

import * as Styled from './styled';

function FiltersPanel(props) {
  function renderHeader() {
    const count = props.countStore.isLoaded ? props.countStore.filterCount : '';

    return (
      <Styled.FiltersHeader>
        <Badge type={BADGE_TYPE.FILTERS}>{count}</Badge>
      </Styled.FiltersHeader>
    );
  }

  return (
    <CollapsablePanel
      maxWidth={328}
      label="Filters"
      renderHeader={renderHeader}
      renderFooter={() => <div></div>}
      panelPosition={PANEL_POSITION.LEFT}
    >
      {props.children}
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
    isLoaded: PropTypes.bool
  }).isRequired,
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ])
};

export default withCollapsablePanel(withCountStore(FiltersPanel));
