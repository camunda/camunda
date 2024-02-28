/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {SequenceFlowsDto} from 'modules/api/processInstances/sequenceFlows';

const getProcessedSequenceFlows = (sequenceFlows: SequenceFlowsDto) => {
  return sequenceFlows
    .map((sequenceFlow) => sequenceFlow.activityId)
    .filter((value, index, self) => self.indexOf(value) === index);
};

export {getProcessedSequenceFlows};
