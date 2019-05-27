/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import equal from 'deep-equal';
import {convertCamelToSpaces} from '../formatters';

export default function reportConfig({view, groupBy, visualization, combinations}) {
  /**
   * Construct a String representing the entry. Suitable for displaying to the user
   *
   * @param config One of the configuration objects of the reportConfig service (view, groupBy, visualization)
   * @param data One data entry of the configuration object. This corresponds to the payload sent to the backend
   */
  const getLabelFor = (config, data) => {
    // special case: variables
    if (data && data.type && data.type.toLowerCase().includes('variable')) {
      return convertCamelToSpaces(data.type) + ': ' + data.value.name;
    }

    for (let i = 0; i < config.length; i++) {
      const entry = config[i];

      if (equal(entry.data, data, {strict: true})) {
        return entry.label;
      }

      if (typeof entry.options === 'object') {
        const sublabel = getLabelFor(entry.options, data);
        if (sublabel) {
          return `${entry.label}: ${sublabel}`;
        }
      }
    }
  };

  /**
   * Checks whether a certain combination of view, groupby and visualization is allowed.
   */
  const isAllowed = (targetView, targetGroupBy, targetVisualization) => {
    const viewGroup = getGroupFor(view, targetView);
    const groupGroup = getGroupFor(groupBy, targetGroupBy);
    const visualizationGroup = getGroupFor(visualization, targetVisualization);

    if (viewGroup && groupGroup && visualizationGroup) {
      return (
        combinations[viewGroup] &&
        combinations[viewGroup][groupGroup] &&
        combinations[viewGroup][groupGroup].includes(visualizationGroup)
      );
    }

    if (viewGroup && groupGroup) {
      return combinations[viewGroup] && combinations[viewGroup][groupGroup];
    }

    return true;
  };

  function getGroupFor(type, data) {
    // special case for variables:
    if (data && data.type && data.type.toLowerCase().includes('variable')) {
      return 'variable';
    }

    const entry = type.find(entry => {
      if (entry.data) {
        return equal(entry.data, data, {strict: true});
      } else if (typeof entry.options === 'object') {
        return entry.options.find(entry => equal(entry.data, data, {strict: true}));
      }
      return false;
    });

    return entry && entry.group;
  }

  function getOnlyOptionFor(type, group) {
    return type.find(entry => entry.group === group).data;
  }

  /**
   * Based on a given view (and optional groupby), returns the next payload data, if it is unambiguous.
   */
  const getNext = (targetView, targetGroupBy) => {
    const viewGroup = getGroupFor(view, targetView);

    const groups = combinations[viewGroup];

    if (!targetGroupBy && Object.keys(groups).length === 1) {
      return getOnlyOptionFor(groupBy, Object.keys(groups)[0]);
    } else if (targetGroupBy) {
      const visualizations = groups[getGroupFor(groupBy, targetGroupBy)];

      if (visualizations.length === 1) {
        return getOnlyOptionFor(visualization, visualizations[0]);
      }
    }
  };

  function update(type, data, props) {
    switch (type) {
      case 'view':
        return updateView(data, props);
      case 'groupBy':
        return updateGroupBy(data, props);
      case 'visualization':
        return updateVisualization(data);
      default:
        throw new Error('Tried to update unknown property');
    }
  }

  function updateView(newView, props) {
    const {groupBy, visualization} = props.report.data;
    const changes = {view: {$set: newView}};

    const newGroup = getNext(newView) || groupBy;
    if (newGroup && !equal(newGroup, groupBy)) {
      changes.groupBy = {$set: newGroup};
    }

    if (!isAllowed(newView, newGroup)) {
      changes.groupBy = {$set: null};
      changes.visualization = {$set: null};

      return changes;
    }

    const newVisualization = getNext(newView, newGroup) || visualization;
    if (newVisualization && newVisualization !== visualization) {
      changes.visualization = {$set: newVisualization};
    }

    if (!isAllowed(newView, newGroup, newVisualization)) {
      changes.visualization = {$set: null};
    }

    return changes;
  }

  function updateGroupBy(newGroupBy, props) {
    const {view, visualization} = props.report.data;

    const changes = {groupBy: {$set: newGroupBy}};

    const newVisualization = getNext(view, newGroupBy);

    if (newVisualization) {
      // if we have a predetermined next visualization, we set it
      changes.visualization = {$set: newVisualization};
    } else if (!isAllowed(view, newGroupBy, visualization)) {
      // if the current visualization is not valid anymore for the new group, we reset it
      changes.visualization = {$set: null};
    }

    return changes;
  }

  function updateVisualization(newVisualization) {
    return {visualization: {$set: newVisualization}};
  }

  return {
    getLabelFor,
    isAllowed,
    update,
    options: {view, groupBy, visualization}
  };
}
