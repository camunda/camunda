import React from 'react';
import PropTypes from 'prop-types';

import CollapsablePanel from 'modules/components/CollapsablePanel';
import ComboBadge from 'modules/components/ComboBadge';

import {applyOperation} from 'modules/api/instances';
import {DIRECTION, OPERATION_TYPE} from 'modules/constants';
import {getSelectionById} from 'modules/utils/selection';

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

  handleDeleteSelection = async deleteId => {
    const {selections, instancesInSelectionsCount, selectionCount} = this.props;

    const selectionToRemove = getSelectionById(selections, deleteId);

    // remove the selection
    selections.splice(selectionToRemove.index, 1);

    await this.props.onStateChange({
      selections,
      instancesInSelectionsCount:
        instancesInSelectionsCount - selectionToRemove.totalCount,
      selectionCount: selectionCount - 1 || 0
    });
    this.props.storeStateLocally({
      selections,
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
        <CollapsablePanel
          maxWidth={479}
          expandButton={
            <Styled.VerticalButton label="Selections">
              <Styled.SelectionsBadge isDefault={!this.props.selectionCount}>
                {this.props.selectionCount}
              </Styled.SelectionsBadge>
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
          <Styled.SelectionHeader isRounded>
            <span>Selections</span>
            <ComboBadge>
              <Styled.SelectionBadgeLeft>
                {this.props.instancesInSelectionsCount}
              </Styled.SelectionBadgeLeft>
              <Styled.SelectionBadgeRight>
                {this.props.selectionCount}
              </Styled.SelectionBadgeRight>
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
        </CollapsablePanel>
      </Styled.Selections>
    );
  }
}
