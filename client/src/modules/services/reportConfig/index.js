import {default as reportConfig} from './reportConfig';
import * as decisionOptions from './decision';
import * as processOptions from './process';

const config = {
  process: reportConfig(processOptions),
  decision: reportConfig(decisionOptions)
};

const processUpdateView = config.process.updateView;
config.process.updateView = (newView, props) => {
  const changes = processUpdateView(newView, props);

  if (newView.property !== 'duration' || newView.entity !== 'processInstance') {
    changes.parameters = {processPart: {$set: null}};
  }

  return changes;
};

export default config;
