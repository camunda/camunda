import React from 'react';
import PropTypes from 'prop-types';

import Dropdown from 'modules/components/Dropdown';
import withSharedState from 'modules/components/withSharedState';

import Paginator from './Paginator';

import {getMaxPage} from './service';
import * as Styled from './styled';

class ListFooter extends React.Component {
  static propTypes = {
    onFirstElementChange: PropTypes.func.isRequired,
    perPage: PropTypes.number.isRequired,
    firstElement: PropTypes.number.isRequired,
    total: PropTypes.number.isRequired,
    onAddToSelection: PropTypes.func.isRequired,
    getStateLocally: PropTypes.func.isRequired,
    storeStateLocally: PropTypes.func.isRequired
  };

  handleSelectionInteraction = () => {
    this.props.onAddToSelection();
  };

  renderOptions = () => {
    return (
      <React.Fragment>
        <Dropdown.Option>Add to Current Selection</Dropdown.Option>
        <Dropdown.Option onClick={this.handleSelectionInteraction}>
          Create Selection
        </Dropdown.Option>
      </React.Fragment>
    );
  };

  renderSelectionDropDown = () => {
    return (
      <Styled.SelectionButton>
        <Dropdown placement="top" label="Add to Selection...">
          {this.renderOptions()}
        </Dropdown>
      </Styled.SelectionButton>
    );
  };

  renderSelectionButton = () => {
    return (
      <Styled.SelectionButton onClick={this.handleSelectionInteraction}>
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
