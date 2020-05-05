/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';

import useInstanceSelectionContext from 'modules/hooks/useInstanceSelectionContext';
import pluralSuffix from 'modules/utils/pluralSuffix';
import {instances} from 'modules/stores/instances';

import Paginator from './Paginator';
import * as Styled from './styled';
import CreateOperationDropdown from './CreateOperationDropdown';

const ListFooter = observer(
  ({perPage, firstElement, onFirstElementChange, hasContent}) => {
    const {filteredInstancesCount} = instances.state;
    const {getSelectedCount} = useInstanceSelectionContext();
    const selectedCount = getSelectedCount();

    const getMaxPage = () => {
      return Math.ceil(filteredInstancesCount / perPage);
    };

    const isPaginationRequired = () => {
      return !(getMaxPage() === 1 || filteredInstancesCount === 0);
    };

    return (
      <Styled.Footer>
        {hasContent && (
          <>
            <Styled.OperationButtonContainer>
              {selectedCount > 0 && (
                <CreateOperationDropdown
                  label={`Apply Operation on ${pluralSuffix(
                    selectedCount,
                    'Instance'
                  )}...`}
                  selectedCount={selectedCount}
                />
              )}
            </Styled.OperationButtonContainer>
            <div>
              {isPaginationRequired() ? (
                <Paginator
                  firstElement={firstElement}
                  perPage={perPage}
                  maxPage={getMaxPage()}
                  onFirstElementChange={onFirstElementChange}
                />
              ) : null}
            </div>
          </>
        )}
        <Styled.Copyright />
      </Styled.Footer>
    );
  }
);

ListFooter.propTypes = {
  onFirstElementChange: PropTypes.func.isRequired,
  perPage: PropTypes.number.isRequired,
  firstElement: PropTypes.number.isRequired,
  hasContent: PropTypes.bool.isRequired,
};

export default ListFooter;
