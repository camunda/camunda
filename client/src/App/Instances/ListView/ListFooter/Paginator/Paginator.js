import React from 'react';
import PropTypes from 'prop-types';

import {LeftBar, Left, Right, RightBar} from 'modules/components/Icon';
import {getRange, getCurrentPage} from './service';

import * as Styled from './styled';

export default class Paginator extends React.Component {
  static propTypes = {
    onFirstElementChange: PropTypes.func.isRequired,
    perPage: PropTypes.number.isRequired,
    firstElement: PropTypes.number.isRequired,
    maxPage: PropTypes.number.isRequired
  };

  handlePageChange = page => () => {
    this.props.onFirstElementChange((page - 1) * this.props.perPage);
  };

  render() {
    const {firstElement, perPage, maxPage} = this.props;

    const currentPage = getCurrentPage(firstElement, perPage);
    const pageRange = getRange(currentPage, maxPage);

    return (
      <Styled.Pagination>
        <Styled.Page
          disabled={currentPage === 1}
          withIcon
          onClick={this.handlePageChange(1)}
        >
          <LeftBar />
        </Styled.Page>
        <Styled.Page
          disabled={currentPage === 1}
          withIcon
          onClick={this.handlePageChange(currentPage - 1)}
        >
          <Left />
        </Styled.Page>
        {!pageRange.includes(1) && (
          <React.Fragment>
            <Styled.Page onClick={this.handlePageChange(1)}>1</Styled.Page>
            {!pageRange.includes(2) && (
              <Styled.PageSeparator>…</Styled.PageSeparator>
            )}
          </React.Fragment>
        )}
        {pageRange.map(page => (
          <Styled.Page
            key={page}
            active={page === currentPage}
            onClick={this.handlePageChange(page)}
          >
            {page}
          </Styled.Page>
        ))}
        {!pageRange.includes(maxPage) && (
          <React.Fragment>
            {!pageRange.includes(maxPage - 1) && (
              <Styled.PageSeparator>…</Styled.PageSeparator>
            )}
            <Styled.Page onClick={this.handlePageChange(maxPage)}>
              {maxPage}
            </Styled.Page>
          </React.Fragment>
        )}
        <Styled.Page
          disabled={currentPage === maxPage}
          withIcon
          onClick={this.handlePageChange(currentPage + 1)}
        >
          <Right />
        </Styled.Page>
        <Styled.Page
          disabled={currentPage === maxPage}
          withIcon
          onClick={this.handlePageChange(maxPage)}
        >
          <RightBar />
        </Styled.Page>
      </Styled.Pagination>
    );
  }
}
