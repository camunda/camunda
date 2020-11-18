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
      ['pie', 'heat'].includes(targetVisualization) &&
      report.data.distributedBy.type !== 'none'
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
    if (
      data?.type?.toLowerCase().includes('variable') ||
      data?.entity?.toLowerCase().includes('variable')
    ) {
      return 'variable';
    }

    const entry = type.find((entry) => {
      if (entry.data) {
        return equal(entry.data, data, {strict: true});
      } else if (Array.isArray(entry.options)) {
        return entry.options.find((entry) => equal(entry.data, data, {strict: true}));
      }
      return false;
    });

    if (entry?.group) {
      return entry.group;
    }

    const option = entry?.options?.find((entry) => equal(entry.data, data, {strict: true}));

    return option?.group;
  }

  function getOnlyOptionFor(type, group) {
    return type.find((entry) => entry.group === group).data;
  }

  /**
   * Based on a given view (and optional groupby), returns the next payload data, if it is unambiguous.
   */
  const getNext = (targetView, targetGroupBy) => {
    const viewGroup = getGroupFor(view, targetView);

    const groups = combinations[viewGroup];

    if (!targetGroupBy) {
      return getOnlyOptionFor(groupBy, Object.keys(groups)[0]);
    } else if (targetGroupBy) {
      const visualizations = groups[getGroupFor(groupBy, targetGroupBy)];

      return getOnlyOptionFor(visualization, visualizations[0]);
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
    const {groupBy: groupByData, visualization} = props.report.data;
    const changes = {view: {$set: newView}};

    let newGroup = groupByData;
    if (!isAllowed(props.report, newView, groupByData) || !groupByData) {
      newGroup = getNext(newView);
      changes.groupBy = {$set: newGroup};
    }

    if (newView.entity !== 'variable') {
      const viewObj = findSelectedOption(view, 'data', newView);
      changes.configuration = {
        yLabel: {
          $set: viewObj.key
            .split('_')
            .map((key) => t('report.view.' + key))
            .join(' '),
        },
      };
      if (newGroup) {
        if (newGroup.type?.toLowerCase().includes('variable')) {
          changes.configuration.xLabel = {$set: newGroup.value.name};
        } else {
          const groupObj = findSelectedOption(groupBy, 'data', newGroup);
          changes.configuration.xLabel = {$set: t('report.groupBy.' + groupObj.key.split('_')[0])};
        }
      }
    }

    if (!isAllowed(props.report, newView, newGroup, visualization) || !visualization) {
      changes.visualization = {$set: getNext(newView, newGroup)};
    }

    return changes;
  }

  function updateGroupBy(newGroupBy, props) {
    const {view, visualization} = props.report.data;

    const changes = {groupBy: {$set: newGroupBy}, configuration: {}};

    if (newGroupBy.type?.toLowerCase().includes('variable')) {
      changes.configuration.xLabel = {$set: newGroupBy.value.name};
    } else {
      const groupObj = findSelectedOption(groupBy, 'data', newGroupBy);
      changes.configuration.xLabel = {$set: t('report.groupBy.' + groupObj.key.split('_')[0])};
    }

    if (!isAllowed(props.report, view, newGroupBy, visualization) || !visualization) {
      changes.visualization = {$set: getNext(view, newGroupBy)};
    }

    return changes;
  }

  function updateVisualization(newVisualization) {
    return {visualization: {$set: newVisualization}};
  }

  function findSelectedOption(options, compareProp, compareValue) {
    for (let i = 0; i < options.length; i++) {
      const option = options[i];
      if (option.options) {
        const found = findSelectedOption(option.options, compareProp, compareValue);
        if (found) {
          return found;
        }
      } else {
        if (equal(option[compareProp], compareValue, {strict: true})) {
          return option;
        }
      }
    }
  }

  return {
    findSelectedOption,
    getLabelFor,
    isAllowed,
    update,
    options: {view, groupBy, visualization},
  };
}
