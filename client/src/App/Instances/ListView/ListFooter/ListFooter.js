import React from 'react';
import PropTypes from 'prop-types';

import Dropdown from 'modules/components/Dropdown';
import ContextualMessage from 'modules/components/ContextualMessage';
import {DROPDOWN_PLACEMENT, CONTEXTUAL_MESSAGE_TYPE} from 'modules/constants';
import {Colors} from 'modules/theme';

import Paginator from './Paginator';
import {getMaxPage, isAnyInstanceSelected} from './service';
import * as Styled from './styled';

export default class ListFooter extends React.Component {
  static propTypes = {
    onFirstElementChange: PropTypes.func.isRequired,
    perPage: PropTypes.number.isRequired,
    firstElement: PropTypes.number.isRequired,
    filterCount: PropTypes.number.isRequired,
    openSelection: PropTypes.number,
    onAddNewSelection: PropTypes.func.isRequired,
    onAddToSpecificSelection: PropTypes.func,
    onAddToOpenSelection: PropTypes.func,
    selections: PropTypes.array,
    selection: PropTypes.object
  };

  isPaginationRequired = (maxPage, total) => {
    return !(maxPage === 1 || total === 0);
  };

  renderButton = () => {
    return (
      <Styled.SelectionButton
        onClick={this.props.onAddNewSelection}
        disabled={!isAnyInstanceSelected(this.props.selection)}
      >
        Create Selection
      </Styled.SelectionButton>
    );
  };

  renderDropDown = () => {
    const {onAddToOpenSelection, onAddNewSelection, selections} = this.props;

    const DropdownButtonStyles = {
      fontSize: '13px',
      fontWeight: 600,
      cursor: 'pointer',
      background: Colors.selections,
      height: '26px',
      borderRadius: '13px',
      border: 'none',
      padding: '4px 11px 5px 11px',
      color: 'rgba(255, 255, 255, 1)'
    };

    return (
      <Dropdown
        placement={DROPDOWN_PLACEMENT.TOP}
        label="Add to Selection..."
        buttonStyles={DropdownButtonStyles}
        disabled={!isAnyInstanceSelected(this.props.selection)}
      >
        {selections.length < 10 ? (
          <Dropdown.Option
            onClick={onAddNewSelection}
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
          onClick={onAddToOpenSelection}
          label="Add to current Selection"
        />
        <Styled.DropdownOption>
          <Dropdown.SubMenu label={'Add to Selection...'}>
            {selections.map(({selectionId}) => (
              <Dropdown.SubOption
                onClick={() => this.props.onAddToSpecificSelection(selectionId)}
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
    const {
      filterCount,
      perPage,
      selections,
      firstElement,
      onFirstElementChange
    } = this.props;

    const maxPage = getMaxPage(filterCount, perPage);
    return (
      <Styled.Footer>
        <Styled.SelectionWrapper>
          {selections.length > 0 ? this.renderDropDown() : this.renderButton()}
        </Styled.SelectionWrapper>
        <Styled.PaginatorWrapper>
          {this.isPaginationRequired(maxPage, filterCount) ? (
            <Paginator
              firstElement={firstElement}
              perPage={perPage}
              maxPage={maxPage}
              onFirstElementChange={onFirstElementChange}
            />
          ) : null}
        </Styled.PaginatorWrapper>
      </Styled.Footer>
    );
  }
}
