import React, {Fragment} from 'react';
import PropTypes from 'prop-types';
import * as Styled from './styled';

import Dropdown from 'modules/components/Dropdown';
import ContextualMessage from 'modules/components/ContextualMessage';
import {withCollapsablePanel} from 'modules/contexts/CollapsablePanelContext';
import {DROPDOWN_PLACEMENT, CONTEXTUAL_MESSAGE_TYPE} from 'modules/constants';

import {isAnyInstanceSelected} from '../service';

class AddSelection extends React.Component {
  static propTypes = {
    selections: PropTypes.array.isRequired,
    onAddNewSelection: PropTypes.func.isRequired,
    selection: PropTypes.object,
    openSelection: PropTypes.number,
    onAddToSpecificSelection: PropTypes.func,
    onAddToOpenSelection: PropTypes.func,
    //from withCollapsablePanel
    isSelectionsCollapsed: PropTypes.bool,
    toggleSelections: PropTypes.func
  };

  openSelectionsPanel = () => {
    if (this.props.isSelectionsCollapsed) {
      this.props.toggleSelections();
    }
  };

  handleAddNewSelection = () => {
    this.props.onAddNewSelection();
    this.openSelectionsPanel();
  };

  handleAddToOpenSelection = () => {
    this.props.onAddToOpenSelection();
  };

  handleAddToSpecificSelection = selectionId => {
    this.props.onAddToSpecificSelection(selectionId);
  };

  renderButton = () => {
    return (
      <Styled.SelectionButton
        onClick={this.handleAddNewSelection}
        disabled={!isAnyInstanceSelected(this.props.selection)}
      >
        Create Selection
      </Styled.SelectionButton>
    );
  };

  renderDropDown = () => {
    const {selections} = this.props;

    return (
      <Dropdown
        placement={DROPDOWN_PLACEMENT.TOP}
        label="Add to Selection..."
        buttonStyles={Styled.DropdownButtonStyles}
        disabled={!isAnyInstanceSelected(this.props.selection)}
        onOpen={this.openSelectionsPanel}
      >
        {selections.length < 10 ? (
          <Dropdown.Option
            onClick={this.handleAddNewSelection}
            label="Create New Selection"
          />
        ) : (
          <Dropdown.Option disabled={true}>
            <Styled.Wrapper>
              <ContextualMessage
                type={CONTEXTUAL_MESSAGE_TYPE.DROP_SELECTION}
              />
            </Styled.Wrapper>
          </Dropdown.Option>
        )}
        <Dropdown.Option
          disabled={!this.props.openSelection}
          onClick={this.handleAddToOpenSelection}
          label="Add to current Selection"
        />
        <Styled.DropdownOption>
          <Dropdown.SubMenu label={'Add to Selection...'}>
            {selections.map(({selectionId}) => (
              <Dropdown.SubOption
                onClick={() => this.handleAddToSpecificSelection(selectionId)}
                key={selectionId}
              >
                {selectionId}
              </Dropdown.SubOption>
            ))}
          </Dropdown.SubMenu>
        </Styled.DropdownOption>
      </Dropdown>
    );
  };

  render() {
    return (
      <Fragment>
        {this.props.selections.length > 0
          ? this.renderDropDown()
          : this.renderButton()}
      </Fragment>
    );
  }
}

export default withCollapsablePanel(AddSelection);
export {AddSelection};
