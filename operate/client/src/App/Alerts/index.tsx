/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {alertsStore} from 'modules/stores/alerts';
import {useEffect} from 'react';

const Alerts = observer(() => {
  useEffect(() => {
    document.title = 'Alerts';

    alertsStore.init();
  }, []);

  return (
    <div>
      <ul>
        {alertsStore.state.alerts?.map((alert) => (
          <li>
            <div>
              {alert.channel.type}: {alert.channel.value}
            </div>
            <div>
              {alert.filters.map((filter) => (
                <div>{filter.processDefinitionKey}</div>
              ))}
            </div>
            <hr />
          </li>
        ))}
      </ul>
    </div>
  );
});

export {Alerts};
