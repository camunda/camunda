/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';

import pluralSuffix from 'modules/utils/pluralSuffix';
import {instancesStore} from 'modules/stores/instances';
import {instanceSelectionStore} from 'modules/stores/instanceSelection';

import {Paginator} from './Paginator';
import * as Styled from './styled';
import CreateOperationDropdown from './CreateOperationDropdown';
import {filtersStore} from 'modules/stores/filters';

const ListFooter = observer(({hasContent}) => {
  const {filteredInstancesCount} = instancesStore.state;
  const selectedCount = instanceSelectionStore.getSelectedInstanceCount();

  const getMaxPage = () => {
    return Math.ceil(
      filteredInstancesCount / filtersStore.state.entriesPerPage
    );
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
              <Paginator maxPage={getMaxPage()} />
            ) : null}
          </div>
        </>
      )}
      <Styled.Copyright />
    </Styled.Footer>
  );
});

ListFooter.propTypes = {
  hasContent: PropTypes.bool.isRequired,
};

export default ListFooter;
