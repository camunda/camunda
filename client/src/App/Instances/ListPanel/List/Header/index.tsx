/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getFilters} from 'modules/utils/filter';
import {useLocation} from 'react-router-dom';
import {observer} from 'mobx-react';
import {instancesStore} from 'modules/stores/instances';
import {instanceSelectionStore} from 'modules/stores/instanceSelection';
import {
  OperationsTH,
  TRHeader,
  CheckAll,
  TH,
  THead,
  SkeletonCheckboxBlock,
} from './styled';
import ColumnHeader from '../ColumnHeader';
import Checkbox from 'modules/components/Checkbox';

const Header = observer(function (props: any) {
  const {
    areProcessInstancesEmpty,
    state: {status},
  } = instancesStore;

  const isInitialDataLoaded = [
    'fetched',
    'fetching-next',
    'fetching-prev',
  ].includes(status);

  const {isAllChecked} = instanceSelectionStore.state;
  const location = useLocation();
  const {canceled, completed} = getFilters(location.search);
  const isListEmpty = !isInitialDataLoaded || areProcessInstancesEmpty;
  const listHasFinishedInstances = canceled || completed;

  return (
    <THead {...props}>
      <TRHeader>
        <TH>
          <CheckAll shouldShowOffset={!isInitialDataLoaded}>
            {isInitialDataLoaded ? (
              <Checkbox
                disabled={isListEmpty}
                isChecked={isAllChecked}
                onChange={instanceSelectionStore.selectAllInstances}
                title="Select all instances"
              />
            ) : (
              <SkeletonCheckboxBlock />
            )}
          </CheckAll>
          <ColumnHeader
            disabled={isListEmpty}
            label="Process"
            sortKey="processName"
          />
        </TH>
        <TH>
          <ColumnHeader
            disabled={isListEmpty}
            label="Instance Id"
            sortKey="id"
          />
        </TH>
        <TH>
          <ColumnHeader
            disabled={isListEmpty}
            label="Version"
            sortKey="processVersion"
          />
        </TH>
        <TH>
          <ColumnHeader
            disabled={isListEmpty}
            label="Start Time"
            sortKey="startDate"
          />
        </TH>
        <TH>
          <ColumnHeader
            disabled={isListEmpty || !listHasFinishedInstances}
            label="End Time"
            sortKey="endDate"
          />
        </TH>
        <TH>
          <ColumnHeader
            disabled={isListEmpty}
            label="Parent Instance Id"
            sortKey="parentInstanceId"
          />
        </TH>
        <OperationsTH>
          <ColumnHeader disabled={isListEmpty} label="Operations" />
        </OperationsTH>
      </TRHeader>
    </THead>
  );
});

export {Header};
