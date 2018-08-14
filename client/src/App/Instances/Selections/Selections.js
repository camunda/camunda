import React from 'react';
import PropTypes from 'prop-types';

import Panel from 'modules/components/Panel';

import {batchRetry} from 'modules/api/selections';
import {BADGE_TYPE, DIRECTION} from 'modules/constants';
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
    handleStateChange: PropTypes.func.isRequired,
    storeStateLocally: PropTypes.func.isRequired,
    filter: PropTypes.object
  };

  toggleSelection = selectionId => {
    this.props.handleStateChange({
      openSelection:
        selectionId !== this.props.openSelection ? selectionId : null
    });
  };

  deleteSelection = async deleteId => {
    const {selections, instancesInSelectionsCount, selectionCount} = this.props;

    const selectionToRemove = getSelectionById(selections, deleteId);

    // remove the selection
    selections.splice(selectionToRemove.index, 1);

    await this.props.handleStateChange({
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

  retrySelection = async evt => {
    evt && evt.stopPropagation();

    try {
      await batchRetry();
    } catch (e) {
      console.log(e);
    }
  };

  render() {
    return (
      <Styled.Selections>
        <Panel isRounded>
          <Styled.SelectionHeader isRounded>
            <span>Selections</span>
            <Styled.Badge
              type={BADGE_TYPE.COMBOSELECTION}
              badgeContent={this.props.instancesInSelectionsCount}
              circleContent={this.props.selectionCount}
            />
          </Styled.SelectionHeader>
          <Panel.Body>
            <SelectionList
              selections={this.props.selections}
              openSelection={this.props.openSelection}
              toggleSelection={this.toggleSelection}
              deleteSelection={this.deleteSelection}
              retrySelection={this.retrySelection}
            />
          </Panel.Body>
          <Styled.RightExpandButton
            direction={DIRECTION.RIGHT}
            isExpanded={true}
          />
          <Panel.Footer />
        </Panel>
      </Styled.Selections>
    );
  }
}
