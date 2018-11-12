import React from 'react';
import PropTypes from 'prop-types';

import CollapsablePanel from 'modules/components/CollapsablePanel';
import Badge from 'modules/components/Badge';
import ComboBadge from 'modules/components/ComboBadge';
import {CollapsablePanelConsumer} from 'modules/contexts/CollapsablePanelContext';

import {applyOperation} from 'modules/api/instances';

import {getSelectionById} from 'modules/utils/selection';
import {serializeInstancesMaps} from 'modules/utils/selection/selection';

import {
  DIRECTION,
  OPERATION_TYPE,
  BADGE_TYPE,
  COMBO_BADGE_TYPE
} from 'modules/constants';

import SelectionList from './SelectionList';

import * as Styled from './styled';

export default class Selections extends React.Component {
  static propTypes = {
    openSelection: PropTypes.number,
    selections: PropTypes.array,
    rollingSelectionIndex: PropTypes.number,
    selectionCount: PropTypes.number,
    instancesInSelectionsCount: PropTypes.number,
    onStateChange: PropTypes.func.isRequired,
    storeStateLocally: PropTypes.func.isRequired,
    filter: PropTypes.object
  };

  executeBatchOperation = async (openSelectionId, operation) => {
    const {selections} = this.props;
    const {queries} = getSelectionById(selections, openSelectionId);

    try {
      await applyOperation(operation, queries);
    } catch (e) {
      console.log(e);
    }
  };

  handleToggleSelection = selectionId => {
    this.props.onStateChange({
      openSelection:
        selectionId !== this.props.openSelection ? selectionId : null
    });
  };

  handleDeleteSelection = async selectionId => {
    const {selections, instancesInSelectionsCount, selectionCount} = this.props;

    const selectionToRemove = getSelectionById(selections, selectionId);
    // remove the selection
    selections.splice(selectionToRemove.index, 1);

    await this.props.onStateChange({
      selections,
      instancesInSelectionsCount:
        instancesInSelectionsCount - selectionToRemove.totalCount,
      selectionCount: selectionCount - 1 || 0
    });
    this.props.storeStateLocally({
      selections: serializeInstancesMaps(selections),
      instancesInSelectionsCount: this.props.instancesInSelectionsCount,
      selectionCount: this.props.selectionCount
    });
  };

  handleRetrySelection = openSelectionId => {
    this.executeBatchOperation(openSelectionId, OPERATION_TYPE.UPDATE_RETRIES);
  };

  handleCancelSelection = openSelectionId => {
    this.executeBatchOperation(openSelectionId, OPERATION_TYPE.CANCEL);
  };

  render() {
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
                    {this.props.selectionCount}
                  </Badge>
                </Styled.VerticalButton>
              }
              collapseButton={
                <Styled.ExpandButton
                  direction={DIRECTION.RIGHT}
                  isExpanded={true}
                  onClick={this.handleCollapse}
                  title="Collapse Selections"
                />
              }
            >
              <Styled.SelectionHeader>
                <span>Selections</span>
                <ComboBadge type={COMBO_BADGE_TYPE.SELECTIONS}>
                  <ComboBadge.Left>{this.props.selectionCount}</ComboBadge.Left>
                  <ComboBadge.Right>
                    {this.props.instancesInSelectionsCount}
                  </ComboBadge.Right>
                </ComboBadge>
              </Styled.SelectionHeader>
              <CollapsablePanel.Body>
                <SelectionList
                  selections={this.props.selections}
                  openSelection={this.props.openSelection}
                  onToggleSelection={this.handleToggleSelection}
                  onDeleteSelection={this.handleDeleteSelection}
                  onRetrySelection={this.handleRetrySelection}
                  onCancelSelection={this.handleCancelSelection}
                />
              </CollapsablePanel.Body>
              <CollapsablePanel.Footer />
            </Styled.CollapsablePanel>
          )}
        </CollapsablePanelConsumer>
      </Styled.Selections>
    );
  }
}
