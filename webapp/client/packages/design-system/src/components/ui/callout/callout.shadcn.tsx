/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `Callout` is a permanent/contextual inline notification (a.k.a.
 * StaticNotification). The vanilla shadcn migration target is `Alert` —
 * the canonical primitive lives at `alert/alert.shadcn.tsx` and is
 * re-exported here for ergonomics.
 */

export {Alert, AlertDescription, AlertTitle} from '../alert/alert.shadcn';
