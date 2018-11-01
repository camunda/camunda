import {ALL_VERSIONS_OPTION, DEFAULT_CONTROLLED_VALUES} from './constants';
import {sortBy} from 'lodash';

/**
 * Creates an array of {value: String, label: String} objects
 * used to create options list for workflowName select based on workflows data
 */
export const getOptionsForWorkflowName = (workflows = {}) => {
  let options = [];
  Object.keys(workflows).forEach(item =>
    options.push({value: item, label: workflows[item].name || item})
  );

  // return case insensitive alphabetically sorted options by "label"
  return sortBy(options, item => item.label.toLowerCase());
};

/**
 * Creates an array of {value: String, label: String} objects
 * used to create options list for workflowIds select based on workflows list
 */
export function getOptionsForWorkflowVersion(versions = []) {
  return versions.map(item => ({
    value: `${item.version}`,
    label: `Version ${item.version}`
  }));
}

/**
 * Pushes an All version option to the given options array
 * used for workflowIds select
 */
export function addAllVersionsOption(options = []) {
  return options.length > 1
    ? [...options, {value: ALL_VERSIONS_OPTION, label: 'All versions'}]
    : [...options];
}

/**
 * Prevents controlled filter fields from receiving undefined values
 * Instances page passes filter prop with the active filters from url
 */
export function getFilterWithDefaults(filter) {
  return {
    ...DEFAULT_CONTROLLED_VALUES,
    ...filter
  };
}

export function getLastVersionOfWorkflow(workflow = {}) {
  let version = '';
  if (workflow.workflows) {
    version = `${workflow.workflows[0].version}`;
  }

  return version;
}
