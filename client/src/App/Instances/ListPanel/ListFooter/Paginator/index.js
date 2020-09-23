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
import {filters} from 'modules/stores/filters';
import {observer} from 'mobx-react';

import * as Styled from './styled';

const Paginator = observer(
  class Paginator extends React.Component {
    static propTypes = {
      maxPage: PropTypes.number.isRequired,
    };

    render() {
      const {maxPage} = this.props;

      const currentPage = getCurrentPage(
        filters.firstElement,
        filters.state.entriesPerPage
      );
      const pageRange = getRange(currentPage, maxPage);

      return (
        <Styled.Pagination>
          <Styled.Page
            title="First page"
            aria-label="First page"
            disabled={currentPage === 1}
            withIcon
            onClick={() => filters.setPage(1)}
          >
            <LeftBar />
          </Styled.Page>
          <Styled.Page
            title="Previous page"
            aria-label="Previous page"
            disabled={currentPage === 1}
            withIcon
            onClick={() => filters.setPage(currentPage - 1)}
          >
            <Left />
          </Styled.Page>
          {!pageRange.includes(1) && (
            <React.Fragment>
              <Styled.Page
                title="Page 1"
                aria-label="Page 1"
                onClick={() => filters.setPage(1)}
              >
                1
              </Styled.Page>
              {!pageRange.includes(2) && (
                <Styled.PageSeparator>…</Styled.PageSeparator>
              )}
            </React.Fragment>
          )}
          {pageRange.map((page) => (
            <Styled.Page
              title={`Page ${page}`}
              aria-label={`Page ${page}`}
              key={page}
              active={page === currentPage}
              onClick={() => filters.setPage(page)}
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
                onClick={() => filters.setPage(maxPage)}
              >
                {maxPage}
              </Styled.Page>
            </React.Fragment>
          )}
          <Styled.Page
            data-test="next-page"
            title="Next page"
            aria-label="Next page"
            disabled={currentPage === maxPage}
            withIcon
            onClick={() => filters.setPage(currentPage + 1)}
          >
            <Right />
          </Styled.Page>
          <Styled.Page
            title="Last page"
            aria-label="Last page"
            disabled={currentPage === maxPage}
            withIcon
            onClick={() => filters.setPage(maxPage)}
          >
            <RightBar />
          </Styled.Page>
        </Styled.Pagination>
      );
    }
  }
);

export {Paginator};
