/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

declare module '@carbon/react' {
  export const Theme: React.FunctionComponent<{
    theme?: 'white' | 'g10' | 'g90' | 'g100';
    className?: string;
  }>;
  export * from 'carbon-components-react';
}
