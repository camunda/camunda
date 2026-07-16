/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// The export makes this file a module, so the declaration below augments
// Carbon's module instead of replacing it.
export {};

// Carbon types DatePickerInput's props as HTMLAttributes (not InputHTMLAttributes),
// omitting input-only attributes even though the component spreads them onto the
// underlying <input>.
declare module '@carbon/react/lib/components/DatePickerInput/DatePickerInput' {
	interface DatePickerInputProps {
		autoComplete?: string;
	}
}
