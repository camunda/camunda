import React from 'react';
import PropTypes from 'prop-types';

import Paginator from './Paginator';
import AddSelection from './AddSelection';
import {getMaxPage} from './service';
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

  render() {
    const {
      filterCount,
      perPage,
      firstElement,
      onFirstElementChange
    } = this.props;
    const maxPage = getMaxPage(filterCount, perPage);

    return (
      <Styled.Footer>
        <Styled.AddSelectionWrapper>
          <AddSelection
            selections={this.props.selections}
            onAddNewSelection={this.props.onAddNewSelection}
            selection={this.props.selection}
            openSelection={this.props.openSelection}
            onAddToSpecificSelection={this.props.onAddToSpecificSelection}
            onAddToOpenSelection={this.props.onAddToOpenSelection}
          />
        </Styled.AddSelectionWrapper>
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
