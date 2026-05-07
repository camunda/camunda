/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `InlineLoading` is a small spinner + status text + transitions
 * between active / finished / error states. shadcn ships only the
 * `Spinner` primitive (a styled `lucide-react` `Loader2Icon`); inline
 * status text and state transitions are app composition. The canonical
 * Spinner lives at `spinner/spinner.shadcn.tsx` and is re-exported here.
 */

export {Spinner} from '../spinner/spinner.shadcn';
