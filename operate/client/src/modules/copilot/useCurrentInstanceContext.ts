/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {matchPath, useLocation} from 'react-router-dom'

interface CurrentInstanceContext {
  readonly processInstanceId: string | null
}

const useCurrentInstanceContext = (): CurrentInstanceContext => {
  const {pathname} = useLocation()
  const match = matchPath(
    {path: '/processes/:processInstanceId/*', end: false},
    pathname,
  )
  return {
    processInstanceId: match?.params.processInstanceId ?? null,
  }
}

export {useCurrentInstanceContext}
export type {CurrentInstanceContext}
