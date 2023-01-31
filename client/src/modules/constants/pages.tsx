/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

const ID_PARAM = ':id';

const Pages = {
  Initial({useIdParam} = {useIdParam: false}) {
    return `/${useIdParam ? `${ID_PARAM}?` : ''}`;
  },
  Login: '/login',
  Tasks: '/:id',
  TaskDetails(id: string = ID_PARAM) {
    return `/${id}`;
  },
  Processes: 'processes',
} as const;

export {Pages};
