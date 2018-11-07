import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default function HeaderSortIcon({
  sorting: {sortBy, sortOrder},
  sortKey,
  handleSorting,
  disabled
}) {
  return (
    <Styled.SortIcon
      sortOrder={!disabled && sortBy === sortKey ? sortOrder : null}
      onClick={() => !disabled && handleSorting(sortKey)}
      title={`Sort by ${sortKey}`}
      disabled={disabled}
    />
  );
}

HeaderSortIcon.propTypes = {
  sorting: PropTypes.object,
  sortKey: PropTypes.string.isRequired,
  handleSorting: PropTypes.func,
  disabled: PropTypes.bool
};

HeaderSortIcon.defaultProps = {
  disabled: false
};
