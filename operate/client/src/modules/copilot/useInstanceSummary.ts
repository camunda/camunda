/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react'
import {endpoints} from '@camunda/camunda-api-zod-schemas/8.10'
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.10'
import {requestWithThrow} from 'modules/request'
import {logger} from 'modules/logger'

interface InstanceSummary {
  readonly state: ProcessInstance['state']
  readonly hasIncident: boolean
}

const useInstanceSummary = (
  processInstanceId: string | null,
): InstanceSummary | null => {
  const [summary, setSummary] = useState<InstanceSummary | null>(null)

  useEffect(() => {
    if (processInstanceId === null) {
      setSummary(null)
      return
    }

    let cancelled = false

    requestWithThrow<ProcessInstance>({
      url: endpoints.getProcessInstance.getUrl({processInstanceKey: processInstanceId}),
      method: 'GET',
    })
      .then(({response, error}) => {
        if (cancelled) return
        if (error !== null || response === null) {
          setSummary(null)
          return
        }
        setSummary({
          state: response.state,
          hasIncident: response.hasIncident ?? false,
        })
      })
      .catch((err) => {
        if (cancelled) return
        logger.error('Failed to load copilot instance summary', err)
        setSummary(null)
      })

    return () => {
      cancelled = true
    }
  }, [processInstanceId])

  return summary
}

export {useInstanceSummary}
export type {InstanceSummary}
