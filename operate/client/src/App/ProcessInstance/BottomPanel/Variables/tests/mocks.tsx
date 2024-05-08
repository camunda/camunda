/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MetaDataDto} from 'modules/api/processInstances/fetchFlowNodeMetaData';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Form} from 'react-final-form';
import arrayMutators from 'final-form-arrays';
import {useEffect} from 'react';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {variablesStore} from 'modules/stores/variables';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {modificationsStore} from 'modules/stores/modifications';
import {Paths} from 'modules/Routes';

type Props = {
  children?: React.ReactNode;
};

const Wrapper: React.FC<Props> = ({children}) => {
  useEffect(() => {
    flowNodeSelectionStore.init();

    return () => {
      processInstanceDetailsStore.reset();
      variablesStore.reset();
      flowNodeSelectionStore.reset();
      processInstanceDetailsStatisticsStore.reset();
      modificationsStore.reset();
    };
  });

  return (
    <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
      <Routes>
        <Route
          path={Paths.processInstance()}
          element={
            <Form onSubmit={() => {}} mutators={{...arrayMutators}}>
              {({handleSubmit}) => {
                return <form onSubmit={handleSubmit}>{children} </form>;
              }}
            </Form>
          }
        />
      </Routes>
    </MemoryRouter>
  );
};

const mockVariables: VariableEntity[] = [
  {
    id: '2251799813686037-clientNo',
    name: 'clientNo',
    value: '"CNT-1211132-0223222"',
    hasActiveOperation: false,
    isFirst: true,
    isPreview: false,
    sortValues: ['clientNo'],
  },
  {
    id: '2251799813686037-mwst',
    name: 'mwst',
    value: '124.26',
    hasActiveOperation: false,
    isFirst: false,
    isPreview: false,
    sortValues: ['mwst'],
  },
  {
    id: '2251799813686037-mwst',
    name: 'active-operation-variable',
    value: '1',
    hasActiveOperation: true,
    isFirst: false,
    isPreview: false,
    sortValues: ['active-operation-variable'],
  },
];

const mockMetaData: MetaDataDto = {
  flowNodeId: null,
  flowNodeInstanceId: '123',
  flowNodeType: 'start-event',
  instanceCount: null,
  instanceMetadata: null,
  incident: null,
  incidentCount: 0,
};

export {Wrapper, mockVariables, mockMetaData};
