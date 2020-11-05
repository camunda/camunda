/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {observer} from 'mobx-react';

import pluralSuffix from 'modules/utils/pluralSuffix';
import {instancesStore} from 'modules/stores/instances';
import {instanceSelectionStore} from 'modules/stores/instanceSelection';

import {Paginator} from './Paginator';
import * as Styled from './styled';
import CreateOperationDropdown from './CreateOperationDropdown';
import {filtersStore} from 'modules/stores/filters';

type Props = {
  hasContent: boolean;
};

const ListFooter: React.FC<Props> = observer(({hasContent}) => {
  const {filteredInstancesCount} = instancesStore.state;
  const selectedCount = instanceSelectionStore.getSelectedInstanceCount();

  const getMaxPage = () => {
    return Math.ceil(
      // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
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
            {/* @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'. */}
            {selectedCount > 0 && (
              <CreateOperationDropdown
                label={`Apply Operation on ${pluralSuffix(
                  selectedCount,
                  'Instance'
                )}...`}
                // @ts-expect-error ts-migrate(2322) FIXME: Type 'null' is not assignable to type 'number'.
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

export default ListFooter;
