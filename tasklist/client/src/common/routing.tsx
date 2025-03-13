/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useParams} from 'react-router-dom';
import {getClientConfig} from 'common/config/getClientConfig';

/* istanbul ignore file */

const pages = {
  initial: '/',
  login: '/login',
  taskDetails(id: string = ':id') {
    return `/${id}`;
  },
  taskDetailsProcess(id: string = ':id') {
    return `/${id}/process`;
  },
  processes(
    options: {tenantId?: string; matchAllChildren?: boolean} = {
      matchAllChildren: false,
    },
  ) {
    const {tenantId, matchAllChildren: matchAllChildren = false} = options;
    const baseRoute = matchAllChildren ? 'processes/*' : 'processes';
    if (tenantId !== undefined && getClientConfig().isMultiTenancyEnabled) {
      return `${baseRoute}?tenantId=${tenantId}`;
    }

    return baseRoute;
  },
  startProcessFromForm: '/new/:bpmnProcessId',
  internalStartProcessFromForm(bpmnProcessId: string = ':bpmnProcessId') {
    return `/processes/${bpmnProcessId}/start`;
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
