/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useParams} from '@remix-run/react';

/* istanbul ignore file */

const pages = {
  initial: '/tasklist',
  login: '/login',
  taskDetails(id: string = ':id') {
    return `/tasklist/${id}`;
  },
  taskDetailsProcess(id: string = ':id') {
    return `/tasklist/${id}/process`;
  },
  processes(
    options: {tenantId?: string; matchAllChildren?: boolean} = {
      matchAllChildren: false,
    },
  ) {
    const {tenantId, matchAllChildren: matchAllChildren = false} = options;
    const baseRoute = matchAllChildren
      ? '/tasklist/processes/*'
      : '/tasklist/processes';
    if (tenantId !== undefined && window.clientConfig?.isMultiTenancyEnabled) {
      return `${baseRoute}?tenantId=${tenantId}`;
    }

    return baseRoute;
  },
  startProcessFromForm: '/new/:bpmnProcessId',
  internalStartProcessFromForm(bpmnProcessId: string = ':bpmnProcessId') {
    return `/tasklist/processes/${bpmnProcessId}/start`;
  },
} as const;

function useTaskDetailsParams(): {id: string} {
  const {id} = useParams<'id'>();

  return {id: id ?? ''};
}

function useStartProcessParams(): {bpmnProcessId: string} {
  const {bpmnProcessId} = useParams<'bpmnProcessId'>();

  return {bpmnProcessId: bpmnProcessId ?? ''};
}

export {pages, useTaskDetailsParams, useStartProcessParams};
