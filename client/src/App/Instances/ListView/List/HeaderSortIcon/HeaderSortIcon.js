import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default function HeaderSortIcon({
  sorting: {sortBy, sortOrder},
  sortKey,
  handleSorting
}) {
  return (
    <Styled.SortIcon
      sortOrder={sortBy === sortKey ? sortOrder : null}
      onClick={() => handleSorting(sortKey)}
    />
  );
}

HeaderSortIcon.propTypes = {
  sorting: PropTypes.object,
  sortKey: PropTypes.string.isRequired,
  handleSorting: PropTypes.func
};
