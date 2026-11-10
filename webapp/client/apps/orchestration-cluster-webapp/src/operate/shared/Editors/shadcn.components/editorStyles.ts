/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const ABOVE_MONACO_LAYERS = 'after:z-50';

const BORDER_OVERLAY = `after:pointer-events-none after:absolute after:inset-0 ${ABOVE_MONACO_LAYERS} after:rounded-lg after:border after:border-input after:ring-inset`;

const INVALID_RING =
	'border-[var(--danger-foreground-subtle)] ring-3 ring-[var(--danger-foreground-subtle)]/20 dark:ring-[var(--danger-foreground-subtle)]/40';

const INVALID_OVERLAY =
	'after:border-[var(--danger-foreground-subtle)] after:ring-3 after:ring-[var(--danger-foreground-subtle)]/20 dark:after:ring-[var(--danger-foreground-subtle)]/40 focus-within:after:border-[var(--danger-foreground-subtle)] focus-within:after:ring-[var(--danger-foreground-subtle)]/20 dark:focus-within:after:ring-[var(--danger-foreground-subtle)]/40';

export {BORDER_OVERLAY, INVALID_RING, INVALID_OVERLAY};
