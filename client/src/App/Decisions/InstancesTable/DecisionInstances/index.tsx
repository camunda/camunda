/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {decisionInstancesStore} from 'modules/stores/decisionInstances';
import {observer} from 'mobx-react';
import React from 'react';
import {Name, State, TD, TR} from '../styled';
import {Link} from 'modules/components/Link';
import {Locations} from 'modules/routes';
import {formatDate} from 'modules/utils/date';
import {useLocation} from 'react-router-dom';

const DecisionInstances = observer(
  React.forwardRef<HTMLTableSectionElement, {}>((_, ref) => {
    const {
      state: {decisionInstances},
    } = decisionInstancesStore;
    const location = useLocation();

    return (
      <tbody ref={ref}>
        {decisionInstances.map(
          ({id, state, name, version, evaluationTime, processInstanceId}) => {
            return (
              <TR key={id}>
                <Name>
                  <State state={state} data-testid={`${state}-icon-${id}`} />
                  {name}
                </Name>
                <TD>
                  <Link
                    to={Locations.decisionInstance(location, id)}
                    title={`View decision instance ${id}`}
                  >
                    {id}
                  </Link>
                </TD>
                <TD>{version}</TD>
                <TD>{formatDate(evaluationTime)}</TD>
                <TD>
                  {processInstanceId !== null ? (
                    <Link
                      to={Locations.instance(location, processInstanceId)}
                      title={`View process instance ${processInstanceId}`}
                    >
                      {processInstanceId}
                    </Link>
                  ) : (
                    'None'
                  )}
                </TD>
              </TR>
            );
          }
        )}
      </tbody>
    );
  })
);

export {DecisionInstances};
