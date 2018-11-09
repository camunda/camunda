import React from 'react';
import PropTypes from 'prop-types';
import * as Styled from './styled';

function ColumnHeader(props) {
  const isSortable = Boolean(props.sortKey);
  const {sortKey, onSort, disabled, sorting} = props;
  const Component = isSortable ? Styled.SortColumnHeader : Styled.ColumnHeader;
  const componentProps = isSortable
    ? {
        disabled,
        onClick: () => {
          !disabled && onSort(sortKey);
        },
        title: `Sort by ${sortKey}`
      }
    : {disabled};

  return (
    <Component {...componentProps}>
      <Styled.Label active={props.active} disabled={props.disabled}>
        {props.label}
      </Styled.Label>
      {isSortable && (
        <Styled.SortIcon
          active={props.active}
          disabled={props.disabled}
          sortOrder={
            !disabled && sorting.sortBy === sortKey ? sorting.sortOrder : null
          }
        />
      )}
    </Component>
  );
}

ColumnHeader.propTypes = {
  active: PropTypes.bool,
  disabled: PropTypes.bool,
  label: PropTypes.string.isRequired,
  sortKey: PropTypes.string,
  onSort: PropTypes.func,
  sorting: PropTypes.object
};

export default ColumnHeader;
