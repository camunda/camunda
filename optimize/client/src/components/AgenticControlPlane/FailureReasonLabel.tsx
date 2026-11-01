/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tag} from '@carbon/react';

import {t} from 'translation';

import type {FailureReason} from './service';

const KNOWN_LIMITS = ['maxModelCalls', 'maxToolCalls', 'maxTokens'];

export function limitLabel(limit: string | null): string {
  if (limit && KNOWN_LIMITS.includes(limit)) {
    return t(`agenticControlPlane.limits.${limit}`) as string;
  }
  return limit ?? (t('agenticControlPlane.failureReason.unknownLimit') as string);
}

// The element a reason belongs to: a limit, the agent element itself, or one of its tools.
export function failureElement(reason: FailureReason): string {
  if (reason.kind === 'LIMIT') {
    return t('agenticControlPlane.failureReason.limitHit', {
      limit: limitLabel(reason.limit),
    }) as string;
  }
  if (reason.toolName) {
    return t('agenticControlPlane.failureReason.tool', {tool: reason.toolName}) as string;
  }
  return t('agenticControlPlane.failureReason.agentElement') as string;
}

export function FailureKindTag({kind}: {kind: FailureReason['kind']}) {
  return (
    <Tag size="sm" type={kind === 'LIMIT' ? 'warm-gray' : 'red'}>
      {t(`agenticControlPlane.failureReason.kind.${kind}`)}
    </Tag>
  );
}

// Compact one-line summary used where several reasons share a cell.
export function FailureReasonLabel({reason}: {reason: FailureReason}) {
  const detail = [reason.errorType, reason.message].filter(Boolean).join(' · ');
  return (
    <>
      <FailureKindTag kind={reason.kind} />
      {failureElement(reason)} ({formatRuns(reason.runs)})
      {detail && <span className="secondary">{detail}</span>}
    </>
  );
}

function formatRuns(runs: number) {
  return t('agenticControlPlane.failureReason.runs', {runs}) as string;
}
