/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import Badge from 'modules/components/Badge';
import ComboBadge from 'modules/components/ComboBadge';
import {CollapsablePanelConsumer} from 'modules/contexts/CollapsablePanelContext';
import {withCountStore} from 'modules/contexts/CountContext';
import {withSelection} from 'modules/contexts/SelectionContext';

import {DIRECTION, BADGE_TYPE, COMBO_BADGE_TYPE} from 'modules/constants';

import SelectionList from './SelectionList';

import * as Styled from './styled';

function Selections(props) {
  const {
    isLoaded,
    selectionCount,
    instancesInSelectionsCount
  } = props.countStore;
  function renderCount() {
    return isLoaded ? selectionCount : '';
  }
  return (
    <Styled.Selections>
      <CollapsablePanelConsumer>
        {context => (
          <Styled.CollapsablePanel
            onCollapse={context.toggleSelections}
            isCollapsed={context.isSelectionsCollapsed}
            maxWidth={479}
            expandButton={
              <Styled.VerticalButton label="Selections">
                <Badge type={BADGE_TYPE.SELECTIONS}>{renderCount()}</Badge>
              </Styled.VerticalButton>
            }
            collapseButton={
              <Styled.CollapseButton
                direction={DIRECTION.RIGHT}
                isExpanded={true}
                title="Collapse Selections"
              />
            }
          >
            <Styled.SelectionHeader>
              <span>Selections</span>
              <ComboBadge type={COMBO_BADGE_TYPE.SELECTIONS}>
                <ComboBadge.Left>{selectionCount}</ComboBadge.Left>
                <ComboBadge.Right>
                  {instancesInSelectionsCount}
                </ComboBadge.Right>
              </ComboBadge>
            </Styled.SelectionHeader>
            <Styled.SelectionBody>
              <SelectionList />
            </Styled.SelectionBody>
          </Styled.CollapsablePanel>
        )}
      </CollapsablePanelConsumer>
    </Styled.Selections>
  );
}

Selections.propTypes = {
  countStore: PropTypes.object
};

export default withSelection(withCountStore(Selections));
