/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {ReactComponent as LeftBar} from 'modules/components/Icon/left-bar.svg';
import {ReactComponent as Left} from 'modules/components/Icon/left.svg';
import {ReactComponent as Right} from 'modules/components/Icon/right.svg';
import {ReactComponent as RightBar} from 'modules/components/Icon/right-bar.svg';
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
          title="First page"
          aria-label="First page"
          disabled={currentPage === 1}
          withIcon
          onClick={this.handlePageChange(1)}
        >
          <LeftBar />
        </Styled.Page>
        <Styled.Page
          title="Previous page"
          aria-label="Previous page"
          disabled={currentPage === 1}
          withIcon
          onClick={this.handlePageChange(currentPage - 1)}
        >
          <Left />
        </Styled.Page>
        {!pageRange.includes(1) && (
          <React.Fragment>
            <Styled.Page
              title="First page"
              aria-label="First page"
              onClick={this.handlePageChange(1)}
            >
              1
            </Styled.Page>
            {!pageRange.includes(2) && (
              <Styled.PageSeparator>…</Styled.PageSeparator>
            )}
          </React.Fragment>
        )}
        {pageRange.map(page => (
          <Styled.Page
            title={`Page ${page}`}
            aria-label={`Page ${page}`}
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
            <Styled.Page
              title={`Page ${maxPage}`}
              aria-label={`Page ${maxPage}`}
              onClick={this.handlePageChange(maxPage)}
            >
              {maxPage}
            </Styled.Page>
          </React.Fragment>
        )}
        <Styled.Page
          title="Next page"
          aria-label="Next page"
          disabled={currentPage === maxPage}
          withIcon
          onClick={this.handlePageChange(currentPage + 1)}
        >
          <Right />
        </Styled.Page>
        <Styled.Page
          title="Last page"
          aria-label="Last page"
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
