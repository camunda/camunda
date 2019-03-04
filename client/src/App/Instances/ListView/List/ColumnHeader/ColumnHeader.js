/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

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

  const isActive = isSortable && props.sorting.sortBy === props.sortKey;

  return (
    <Component {...componentProps}>
      <Styled.Label active={isActive} disabled={props.disabled}>
        {props.label}
      </Styled.Label>
      {isSortable && (
        <Styled.SortIcon
          active={isActive}
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
  disabled: PropTypes.bool,
  label: PropTypes.string.isRequired,
  sortKey: PropTypes.string,
  onSort: PropTypes.func,
  sorting: PropTypes.object
};

export default ColumnHeader;
