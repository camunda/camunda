/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {GetTasks, GetTasksVariables} from 'modules/queries/get-tasks';
import {graphql, RequestHandler} from 'msw';

const handlers: RequestHandler[] = [
  graphql.query<GetTasks, Omit<GetTasksVariables, 'sort'>>(
    'GetTasks',
    async (req, res, ctx) => {
      const body = await req.json();
      const {sort: _, ...variables} = body.variables;

      const originalResponse = await ctx.fetch(req.url.href, {
        headers: req.headers,
        method: req.method,
        body: JSON.stringify({
          operationName: body.operationName,
          query: body.query
            .replace('followUpDate\n    dueDate\n    ', '')
            .replace(', $sort: [TaskOrderBy!]', '')
            .replace(', sort: $sort', ''),
          variables,
        }),
      });
      const {
        data: {tasks},
      } = (await originalResponse.json()) as {data: GetTasks};

      return res(
        ctx.data({
          tasks: tasks.map((task, index) => ({
            ...task,
            followUpDate: index % 2 === 0 ? '2025-01-01T00:00:00.000Z' : null,
            dueDate: index % 2 === 0 ? '2024-01-01T00:00:00.000Z' : null,
          })),
        }),
      );
    },
  ),
];

export {handlers};
