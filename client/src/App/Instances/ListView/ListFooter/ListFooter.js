import React from 'react';
import PropTypes from 'prop-types';

import Dropdown from 'modules/components/Dropdown';
import withSharedState from 'modules/components/withSharedState';
import {DROPDOWN_PLACEMENT} from 'modules/constants';

import Paginator from './Paginator';

import {getMaxPage} from './service';
import {
  DROPDOWN_LABEL,
  DROPDOWN_CREATE_OPTION,
  DROPDOWN_ADD_CURRENT_OPTION
} from './constants';
import * as Styled from './styled';

class ListFooter extends React.Component {
  static propTypes = {
    onFirstElementChange: PropTypes.func.isRequired,
    perPage: PropTypes.number.isRequired,
    firstElement: PropTypes.number.isRequired,
    total: PropTypes.number.isRequired,
    handleAddNewSelection: PropTypes.func.isRequired,
    getStateLocally: PropTypes.func.isRequired,
    storeStateLocally: PropTypes.func.isRequired,
    onAddToOpenSelection: PropTypes.func
  };

  renderSelectionDropDown = () => {
    return (
      <Styled.SelectionButton>
        <Dropdown placement={DROPDOWN_PLACEMENT.TOP} label={DROPDOWN_LABEL}>
          <Dropdown.Option onClick={this.props.onAddToOpenSelection}>
            {DROPDOWN_ADD_CURRENT_OPTION}
          </Dropdown.Option>
          <Dropdown.Option onClick={this.props.handleAddNewSelection}>
            {DROPDOWN_CREATE_OPTION}
          </Dropdown.Option>
        </Dropdown>
      </Styled.SelectionButton>
    );
  };

  renderSelectionButton = () => {
    return (
      <Styled.SelectionButton onClick={this.props.handleAddNewSelection}>
        Create Selection
      </Styled.SelectionButton>
    );
  };

  isPaginationRequired = (maxPage, total) => {
    return !(maxPage === 1 || total === 0);
  };

  render() {
    const maxPage = getMaxPage(this.props.total, this.props.perPage);
    return (
      <React.Fragment>
        {this.props.getStateLocally().selections
          ? this.renderSelectionDropDown()
          : this.renderSelectionButton()}
        {this.isPaginationRequired(maxPage, this.props.total) ? (
          <Paginator
            firstElement={this.props.firstElement}
            perPage={this.props.perPage}
            maxPage={maxPage}
            onFirstElementChange={this.props.onFirstElementChange}
          />
        ) : null}
      </React.Fragment>
    );
  }
}

const WrappedFooter = withSharedState(ListFooter);
WrappedFooter.WrappedComponent = ListFooter;

export default WrappedFooter;
