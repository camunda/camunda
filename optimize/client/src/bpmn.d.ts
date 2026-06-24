/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

declare module '@bpmn-io/element-templates-icons-renderer' {}

declare module 'bpmn-js-disable-collapsed-subprocess';

declare module '@bpmn-io/dmn-migrate' {
  declare function migrateDiagram(xml: string | null): Promise<string>;
}
