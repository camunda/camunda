/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import getDataKeys from '../getDataKeys';

export default function reportConfig({view, groupBy, visualization}) {
  /**
   * Retrieves the first level subobject form the configuration that corresponds to the entry. This does not fetch any submenu entries.
   *
   * @param config One of the configuration objects of the reportConfig service (view, groupBy, visualization)
   * @param entry One data entry of the configuration object. This corresponds to the payload sent to the backend
   */
  const getObject = (config, entry) => {
    if (!entry || !config) {
      return;
    }
    const keys = Object.keys(config);
    for (let i = 0; i < keys.length; i++) {
      const key = keys[i];
      const {data} = config[key];

      const dataKeys = getDataKeys(data);
      if (
        dataKeys.every(
          prop =>
            JSON.stringify(entry[prop]) === JSON.stringify(data[prop]) || Array.isArray(data[prop])
        )
      ) {
        return config[key];
      }
    }
  };

  /**
   * Construct a String representing the entry. Suitable for displaying to the user
   *
   * @param config One of the configuration objects of the reportConfig service (view, groupBy, visualization)
   * @param entry One data entry of the configuration object. This corresponds to the payload sent to the backend
   */
  const getLabelFor = (config, entry, xml) => {
    const obj = getObject(config, entry);

    if (obj) {
      const {data, label} = obj;

      if (data.type === 'variable') {
        return `${label}: ${entry.value.name}`;
      }

      if (data.type === 'inputVariable' || data.type === 'outputVariable') {
        return `${label}: ${new DOMParser()
          .parseFromString(xml, 'text/xml')
          .querySelector(`[id="${entry.value.id}"]`)
          .getAttribute('label')}`;
      }

      const dataKeys = getDataKeys(data);

      const submenu = dataKeys.find(key => Array.isArray(data[key]));
      if (submenu) {
        return `${label}: ${getLabelFor(data[submenu], entry[submenu], xml)}`;
      }
      return label;
    }
  };

  const getNextObject = (view, targetView) => {
    const {data} = view;
    const dataKeys = getDataKeys(data);

    let next = view.next;

    const submenu = dataKeys.find(key => Array.isArray(data[key]));
    if (submenu) {
      next = getObject(data[submenu], targetView[submenu]).next;
    }

    return next;
  };

  /**
   * Checks whether a certain combination of view, groupby and visualization is allowed.
   */
  const isAllowed = (targetView, targetGroupBy, targetVisualization) => {
    const viewObj = getObject(view, targetView);
    const groupByObj = getObject(groupBy, targetGroupBy);
    const visualizationObj = getObject(visualization, targetVisualization);

    if (viewObj && groupByObj) {
      const next = getNextObject(viewObj, targetView);
      const allowed = next.find(({entity}) => entity === groupByObj);
      if (!allowed) {
        return false;
      }

      if (visualizationObj) {
        return !!allowed.then.find(potentialVis => visualizationObj === potentialVis);
      }
    }

    return true;
  };

  /**
   * Based on a given view (and optional groupby), returns the next payload data, if it is unambiguous.
   */
  const getNext = (targetView, targetGroupBy) => {
    const viewObj = getObject(view, targetView);
    const groupByObj = getObject(groupBy, targetGroupBy);

    const next = getNextObject(viewObj, targetView);

    if (next.length === 1 && !targetGroupBy) {
      return next[0].entity.data;
    }

    if (groupByObj) {
      const allowed = next.find(({entity}) => entity === groupByObj);
      if (!allowed) {
        return;
      }

      if (allowed.then.length === 1) {
        return allowed.then[0].data;
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
    // we need to compare the string representation for changes, because groupBy is an object, not a string
    if (newGroup && JSON.stringify(newGroup) !== JSON.stringify(groupBy)) {
      changes.groupBy = {$set: newGroup};
    }

    const newVisualization = getNext(newView, newGroup) || visualization;
    if (newVisualization && newVisualization !== visualization) {
      changes.visualization = {$set: newVisualization};
    }

    if (!isAllowed(newView, newGroup)) {
      changes.groupBy = {$set: null};
      changes.visualization = {$set: null};
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
    getNext,
    update,
    options: {view, groupBy, visualization}
  };
}
