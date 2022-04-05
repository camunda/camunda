/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Fragment} from 'react';
import {observer} from 'mobx-react';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {Locations} from 'modules/routes';
import {Container, Link, Separator, CurrentInstance} from './styled';
import {tracking} from 'modules/tracking';
import {useLocation} from 'react-router-dom';

const Breadcrumb: React.FC = observer(() => {
  const location = useLocation();

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
              to={Locations.instance(location, instanceId)}
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
