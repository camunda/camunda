import React from 'react';

import * as Styled from './styled';
import {LeftBar, Left, Right, RightBar} from 'modules/components/Icon';

export default class InstancesListFooter extends React.Component {
  handlePageChange = page => () => {
    this.props.onFirstElementChange((page - 1) * this.props.perPage);
  };

  render() {
    if (typeof this.props.total !== 'number') {
      return null;
    }
    const currentPage =
      Math.round(this.props.firstElement / this.props.perPage) + 1;
    const maxPage = Math.ceil(this.props.total / this.props.perPage);

    const pageRange = getRange(currentPage, maxPage);

    return (
      <Styled.Pagination>
        <Styled.Page disabled={currentPage === 1} withIcon>
          <LeftBar />
        </Styled.Page>
        <Styled.Page disabled={currentPage === 1} withIcon>
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
        <Styled.Page disabled={currentPage === maxPage} withIcon>
          <Right />
        </Styled.Page>
        <Styled.Page disabled={currentPage === maxPage} withIcon>
          <RightBar />
        </Styled.Page>
      </Styled.Pagination>
    );
  }
}

function getRange(focus, max) {
  let start = Math.max(focus - 2, 1);
  let end = Math.min(start + 4, max);
  if (max - focus < 2) {
    start = Math.max(end - 4, 1);
  }

  const pages = [];
  for (let i = start; i <= end; i++) {
    pages.push(i);
  }

  return pages;
}
