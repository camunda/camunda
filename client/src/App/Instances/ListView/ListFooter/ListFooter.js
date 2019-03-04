/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

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
    filterCount: PropTypes.number.isRequired
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
          <AddSelection />
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
