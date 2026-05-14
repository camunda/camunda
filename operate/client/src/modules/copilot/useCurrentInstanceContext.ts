/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useLocation} from 'react-router-dom'

interface CurrentInstanceContext {
  readonly processInstanceId: string | null
}

const INSTANCE_PATH = /^\/processes\/([^/]+)/

const useCurrentInstanceContext = (): CurrentInstanceContext => {
  const {pathname} = useLocation()
  const match = INSTANCE_PATH.exec(pathname)
  return {
    processInstanceId: match?.[1] ?? null,
  }
}

export {useCurrentInstanceContext}
export type {CurrentInstanceContext}
