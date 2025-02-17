/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import {observer} from 'mobx-react';
import {fetchProcessDefinitions} from 'modules/api/v2/process-definition';
import {alertsStore} from 'modules/stores/alerts';
import {useEffect, useState} from 'react';

const Alerts = observer(() => {
  const [processDefinitions, setProcessDefinitions] = useState<any[]>([]);

  useEffect(() => {
    document.title = 'Alerts';

    alertsStore.init();

    async function fetchIt() {
      const response = await fetchProcessDefinitions();

      const data = await response.json();

      setProcessDefinitions(data.items);
    }

    fetchIt();
  }, []);

  const rows = alertsStore.state.alerts?.map((alert) => {
    console.log(processDefinitions);

    return {
      id: alert.filters[0].processDefinitionKey,
      email: alert.channel.value,
      definition: processDefinitions.find(({processDefinitionKey}) => {
        return processDefinitionKey === alert.filters[0].processDefinitionKey;
      }),
    };
  });

  return (
    <div style={{padding: '20px'}}>
      <Table aria-label="sample table">
        <TableHead>
          <TableRow>
            <TableHeader id="process" key={1}>
              Process
            </TableHeader>
            <TableHeader id="alert-timing" key={2}>
              Alert timing
            </TableHeader>
            <TableHeader id="channel" key={3}>
              Notification Channel
            </TableHeader>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows &&
            rows.map((row) => (
              <TableRow key={row.id}>
                <TableCell key={1}>
                  {row.definition?.processDefinitionId ?? row.id}
                </TableCell>
                <TableCell key={2}>Immediate</TableCell>
                <TableCell key={3}>{`Email: ${row.email}`}</TableCell>
              </TableRow>
            ))}
        </TableBody>
      </Table>
    </div>
  );
});

export {Alerts};
