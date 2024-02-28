/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ProcessDto} from 'modules/api/processes/fetchGroupedProcesses';
import {CopiableContent} from 'modules/components/PanelHeader/CopiableContent';

type Props = {
  bpmnProcessId?: ProcessDto['bpmnProcessId'];
};

const CopiableProcessID: React.FC<Props> = ({bpmnProcessId}) => {
  if (bpmnProcessId === undefined) {
    return null;
  }

  return (
    <CopiableContent
      copyButtonDescription="Process ID / Click to copy"
      content={bpmnProcessId}
    />
  );
};

export {CopiableProcessID};
