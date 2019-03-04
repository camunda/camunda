/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Fragment} from 'react';
import PropTypes from 'prop-types';
import * as Styled from './styled';

import Dropdown from 'modules/components/Dropdown';
import ContextualMessage from 'modules/components/ContextualMessage';
import {DROPDOWN_PLACEMENT, CONTEXTUAL_MESSAGE_TYPE} from 'modules/constants';
import {withCollapsablePanel} from 'modules/contexts/CollapsablePanelContext';
import {withSelection} from 'modules/contexts/SelectionContext';

import {isAnyInstanceSelected} from '../service';

class AddSelection extends React.Component {
  static propTypes = {
    onAddNewSelection: PropTypes.func.isRequired,
    onAddToSelectionById: PropTypes.func.isRequired,
    onAddToOpenSelection: PropTypes.func.isRequired,
    expandSelections: PropTypes.func.isRequired,
    selections: PropTypes.array.isRequired,
    selectedInstances: PropTypes.object,
    openSelection: PropTypes.number
  };

  handleAddNewSelection = () => {
    this.props.onAddNewSelection();
    this.props.expandSelections();
  };

  renderButton = () => {
    return (
      <Styled.SelectionButton
        onClick={this.handleAddNewSelection}
        disabled={!isAnyInstanceSelected(this.props.selectedInstances)}
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
        buttonStyles={Styled.dropdownButtonStyles}
        disabled={!isAnyInstanceSelected(this.props.selectedInstances)}
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
          onClick={this.props.onAddToOpenSelection}
          label="Add to current Selection"
        />
        <Styled.DropdownOption>
          <Dropdown.SubMenu label={'Add to Selection...'}>
            {selections.map(({selectionId}) => (
              <Dropdown.SubOption
                onClick={() => this.props.onAddToSelectionById(selectionId)}
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

export default withSelection(withCollapsablePanel(AddSelection));
