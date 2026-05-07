/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `InlineNotification` is a dismissible inline banner with a
 * status icon, title, subtitle, and an X close button. shadcn ships only
 * the `Alert` primitive — dismissibility, status iconography, and the
 * close button are app composition. The canonical `Alert` lives at
 * `alert/alert.shadcn.tsx` and is re-exported here for ergonomics.
 */

export {Alert, AlertDescription, AlertTitle} from '../alert/alert.shadcn';
