import {default as reportConfig} from './reportConfig';
import * as decisionOptions from './decision';
import * as processOptions from './process';

export const processConfig = reportConfig(processOptions);
export const decisionConfig = reportConfig(decisionOptions);

const processUpdateView = processConfig.updateView;
processConfig.updateView = (newView, props) => {
  const changes = processUpdateView(newView, props);

  if (newView.property !== 'duration' || newView.entity !== 'processInstance') {
    changes.parameters = {processPart: {$set: null}};
  }

  return changes;
};
