/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getProcessInstanceFilters} from 'modules/utils/filter';
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
  SelectAllCheckbox,
} from './styled';
import ColumnHeader from '../ColumnHeader';
import {Restricted} from 'modules/components/Restricted';

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
  const {canceled, completed} = getProcessInstanceFilters(location.search);
  const isListEmpty = !isInitialDataLoaded || areProcessInstancesEmpty;
  const listHasFinishedInstances = canceled || completed;

  return (
    <THead {...props}>
      <TRHeader>
        <TH>
          <CheckAll shouldShowOffset={!isInitialDataLoaded}>
            <Restricted scopes={['write']}>
              {isInitialDataLoaded ? (
                <SelectAllCheckbox
                  title="Select all instances"
                  checked={isAllChecked}
                  onCmInput={instanceSelectionStore.selectAllInstances}
                  disabled={isListEmpty}
                />
              ) : (
                <SkeletonCheckboxBlock />
              )}
            </Restricted>
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
        <Restricted scopes={['write']}>
          <OperationsTH>
            <ColumnHeader disabled={isListEmpty} label="Operations" />
          </OperationsTH>
        </Restricted>
      </TRHeader>
    </THead>
  );
});

export {Header};
