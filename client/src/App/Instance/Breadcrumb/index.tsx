/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Fragment} from 'react';
import {observer} from 'mobx-react';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {Locations} from 'modules/routes';
import {Container, Link, Separator, CurrentInstance} from './styled';

const Breadcrumb: React.FC = observer(() => {
  if (
    currentInstanceStore.state.instance === null ||
    currentInstanceStore.state.instance.callHierarchy.length === 0
  ) {
    return null;
  }

  const {id, callHierarchy, processName} = currentInstanceStore.state.instance;
  return (
    <Container>
      {callHierarchy.map(({instanceId, processDefinitionName}) => {
        return (
          <Fragment key={instanceId}>
            <Link
              to={(location) => Locations.instance(instanceId, location)}
              title={`View Process ${processDefinitionName} - Instance ${instanceId}`}
            >
              {`${processDefinitionName}`}
            </Link>
            <Separator>›</Separator>
          </Fragment>
        );
      })}
      <CurrentInstance title={`Process ${processName} - Instance ${id}`}>
        {processName}
      </CurrentInstance>
    </Container>
  );
});

export {Breadcrumb};
