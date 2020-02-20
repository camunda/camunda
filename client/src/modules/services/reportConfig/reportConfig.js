/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import equal from 'deep-equal';
import {t} from 'translation';

export default function reportConfig({view, groupBy, visualization, combinations}) {
  /**
   * Construct a String representing the entry. Suitable for displaying to the user
   *
   * @param type type of the menu (View, GroupBy, Visualization)
   * @param menu One of the type arrays of the reportConfig service (view, groupBy, visualization)
   * @param data One data entry of the type array. This corresponds to the payload sent to the backend
   * @param subOption defines whethet the data entry we are searching for is a sub option or not
   */
  const getLabelFor = (type, menu, data, subOption = false) => {
    // special case: variables
    if (data && data.type && data.type.toLowerCase().includes('variable')) {
      return t(`report.${type}.${data.type}`) + ': ' + data.value.name;
    }

    for (let i = 0; i < menu.length; i++) {
      const entry = menu[i];

      if (equal(entry.data, data, {strict: true})) {
        let key;

        if (subOption) {
          key = entry.key.split('_')[1];
        } else {
          key = entry.key;
        }
        return t(`report.${type}.${key}`);
      }

      if (Array.isArray(entry.options)) {
        const subLabel = getLabelFor(type, entry.options, data, true);
        if (subLabel) {
          return t(`report.${type}.${entry.key}`) + ': ' + subLabel;
        }
      }
    }
  };

  /**
   * Checks whether a certain combination of view, groupby and visualization is allowed.
   */
  const isAllowed = (report, targetView, targetGroupBy, targetVisualization) => {
    const viewGroup = getGroupFor(view, targetView);
    const groupGroup = getGroupFor(groupBy, targetGroupBy);
    const visualizationGroup = getGroupFor(visualization, targetVisualization);

    if (
      ['line', 'pie', 'heat'].includes(targetVisualization) &&
      ['user', 'fn'].includes(groupGroup) &&
      report.data.configuration.distributedBy !== 'none'
    ) {
      return false;
    }

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
      } else if (Array.isArray(entry.options)) {
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

    if (!isAllowed(props.report, newView, newGroup)) {
      changes.groupBy = {$set: null};
      changes.visualization = {$set: null};

      return changes;
    }

    const newVisualization = getNext(newView, newGroup) || visualization;
    if (newVisualization && newVisualization !== visualization) {
      changes.visualization = {$set: newVisualization};
    }

    if (!isAllowed(props.report, newView, newGroup, newVisualization)) {
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
    } else if (!isAllowed(props.report, view, newGroupBy, visualization)) {
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
