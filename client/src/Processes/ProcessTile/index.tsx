/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

type Props = {
  name: string | null;
  processDefinitionId: string;
  isStartButtonDisabled: boolean;
  'data-testid'?: string;
};

const ProcessTile: React.FC<Props> = (props) => {
  return <div data-testid={props['data-testid']} />;
};

export {ProcessTile};
