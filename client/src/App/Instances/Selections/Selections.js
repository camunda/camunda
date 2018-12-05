import React from 'react';
import PropTypes from 'prop-types';

import CollapsablePanel from 'modules/components/CollapsablePanel';
import Badge from 'modules/components/Badge';
import ComboBadge from 'modules/components/ComboBadge';
import {CollapsablePanelConsumer} from 'modules/contexts/CollapsablePanelContext';
import {withSelection} from 'modules/contexts/SelectionContext';

import {applyOperation} from 'modules/api/instances';

import {getSelectionById} from 'modules/utils/selection';

import {
  DIRECTION,
  OPERATION_TYPE,
  BADGE_TYPE,
  COMBO_BADGE_TYPE
} from 'modules/constants';

import SelectionList from './SelectionList';

import * as Styled from './styled';

class Selections extends React.Component {
  static propTypes = {
    openSelection: PropTypes.number,
    selections: PropTypes.array,
    selectionCount: PropTypes.number,
    instancesInSelectionsCount: PropTypes.number,
    onToggleSelection: PropTypes.func.isRequired,
    onDeleteSelection: PropTypes.func.isRequired
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
                  onToggleSelection={this.props.onToggleSelection}
                  onDeleteSelection={this.props.onDeleteSelection}
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

export default withSelection(Selections);
