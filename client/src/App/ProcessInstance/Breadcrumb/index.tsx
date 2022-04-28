/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Fragment} from 'react';
import {observer} from 'mobx-react';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {Paths} from 'modules/routes';
import {Container, Link, Separator, CurrentInstance} from './styled';
import {tracking} from 'modules/tracking';

const Breadcrumb: React.FC = observer(() => {
  if (
    processInstanceDetailsStore.state.processInstance === null ||
    processInstanceDetailsStore.state.processInstance.callHierarchy.length === 0
  ) {
    return null;
  }

  const {id, callHierarchy, processName} =
    processInstanceDetailsStore.state.processInstance;
  return (
    <Container>
      {callHierarchy.map(({instanceId, processDefinitionName}) => {
        return (
          <Fragment key={instanceId}>
            <Link
              to={Paths.processInstance(instanceId)}
              title={`View Process ${processDefinitionName} - Instance ${instanceId}`}
              onClick={() => {
                tracking.track({
                  eventName: 'navigation',
                  link: 'instance-breadcrumb',
                });
              }}
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
