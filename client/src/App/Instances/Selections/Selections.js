/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import CollapsablePanel from 'modules/components/CollapsablePanel';
import Badge from 'modules/components/Badge';
import ComboBadge from 'modules/components/ComboBadge';
import {CollapsablePanelConsumer} from 'modules/contexts/CollapsablePanelContext';
import {withSelection} from 'modules/contexts/SelectionContext';

import {DIRECTION, BADGE_TYPE, COMBO_BADGE_TYPE} from 'modules/constants';

import SelectionList from './SelectionList';

import * as Styled from './styled';

function Selections(props) {
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
                <Badge type={BADGE_TYPE.SELECTIONS}>
                  {props.selectionCount}
                </Badge>
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
                <ComboBadge.Left>{props.selectionCount}</ComboBadge.Left>
                <ComboBadge.Right>
                  {props.instancesInSelectionsCount}
                </ComboBadge.Right>
              </ComboBadge>
            </Styled.SelectionHeader>
            <CollapsablePanel.Body>
              <SelectionList />
            </CollapsablePanel.Body>
            <CollapsablePanel.Footer />
          </Styled.CollapsablePanel>
        )}
      </CollapsablePanelConsumer>
    </Styled.Selections>
  );
}

Selections.propTypes = {
  selectionCount: PropTypes.number,
  instancesInSelectionsCount: PropTypes.number
};

export default withSelection(Selections);
