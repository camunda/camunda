/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {Container, PanelHeader, ErrorMessage} from './styled';
import {TimeStampPill} from './TimeStampPill';
import {modificationsStore} from 'modules/stores/modifications';
import {Stack} from '@carbon/react';
import {Skeleton} from './Skeleton';
import {ExecutionCountToggle} from './ExecutionCountToggle';
import {ElementInstancesTree} from './ElementInstancesTree';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';

const Layout: React.FC<{children: React.ReactNode}> = observer(({children}) => {
  return (
    <Container data-testid="instance-history">
      <PanelHeader title="Instance History" size="sm">
        {!modificationsStore.isModificationModeEnabled && (
          <Stack orientation="horizontal" gap={5}>
            <TimeStampPill />
            <ExecutionCountToggle />
          </Stack>
        )}
      </PanelHeader>
      {children}
    </Container>
  );
});

const ElementInstanceLog: React.FC = observer(() => {
  const {data: processInstance, status: processInstanceStatus} =
    useProcessInstance();
  const {data: businessObjects, status: businessObjectsStatus} =
    useBusinessObjects();

  if ([processInstanceStatus, businessObjectsStatus].includes('pending')) {
    return (
      <Layout>
        <Skeleton />
      </Layout>
    );
  }

  if ([processInstanceStatus, businessObjectsStatus].includes('error')) {
    return (
      <Layout>
        {/* TODO update the message with 403 related error during v2 endpoint integration #33542 */}
        <ErrorMessage message="Instance History could not be fetched" />
      </Layout>
    );
  }

  return (
    <Layout>
      <ElementInstancesTree
        processInstance={processInstance!}
        businessObjects={businessObjects!}
        errorMessage={
          <ErrorMessage message="Instance History could not be fetched" />
        }
      />
    </Layout>
  );
});

export {ElementInstanceLog};
