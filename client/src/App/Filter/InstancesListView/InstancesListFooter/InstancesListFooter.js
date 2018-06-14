import React from 'react';
import Panel from 'modules/components/Panel';

import * as Styled from './styled';
import {LeftBar, Left, Right, RightBar} from 'modules/components/Icon';

export default class InstancesListFooter extends React.Component {
  render() {
    if (typeof this.props.total !== 'number') {
      return null;
    }
    const currentPage =
      Math.round(this.props.firstElement / this.props.perPage) + 1;
    const maxPage = Math.ceil(this.props.total / this.props.perPage);

    const pageRange = getRange(currentPage, maxPage);

    return (
      <Panel.Footer>
        <Styled.Pagination>
          <Styled.Page disabled={currentPage === 1} withIcon>
            <LeftBar />
          </Styled.Page>
          <Styled.Page disabled={currentPage === 1} withIcon>
            <Left />
          </Styled.Page>
          {!pageRange.includes(1) && (
            <React.Fragment>
              <Styled.Page>1</Styled.Page>
              <Styled.PageSeparator>…</Styled.PageSeparator>
            </React.Fragment>
          )}
          {pageRange.map(page => (
            <Styled.Page key={page} active={page === currentPage}>
              {page}
            </Styled.Page>
          ))}
          {!pageRange.includes(maxPage) && (
            <React.Fragment>
              <Styled.PageSeparator>…</Styled.PageSeparator>
              <Styled.Page>{maxPage}</Styled.Page>
            </React.Fragment>
          )}
          <Styled.Page disabled={currentPage === maxPage} withIcon>
            <Right />
          </Styled.Page>
          <Styled.Page disabled={currentPage === maxPage} withIcon>
            <RightBar />
          </Styled.Page>
        </Styled.Pagination>
      </Panel.Footer>
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
