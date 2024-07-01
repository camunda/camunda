/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useCallback, useMemo} from 'react';

import {DMNDiagram} from 'components';
import {themed} from 'theme';
import {formatters} from 'services';

import DmnJsPortal from './DmnJsPortal';
import createHitsColumnAddon from './HitsColumnAddon';

import './DecisionTable.scss';

export function DecisionTable({report, theme}) {
  const {
    result: {data, instanceCount},
    data: {
      definitions: [{key}],
      configuration: {xml, hideAbsoluteValue, hideRelativeValue, showGradientBars},
    },
  } = report;

  const resultObj = useMemo(() => formatters.objectifyResult(data), [data]);
  const [entryPoints, setEntryPoints] = useState({rules: {}});

  const renderRuleCell = (ruleId) => {
    const resultNumber = resultObj[ruleId] || 0;
    const percentage = Math.round((resultNumber / instanceCount) * 1000) / 10 || 0;

    const node = entryPoints.rules[ruleId];
    if (showGradientBars) {
      const progress = resultNumber / instanceCount;

      node.style.background = `linear-gradient(to right, ${getColor(theme, 0)} 0%, ${getColor(
        theme,
        progress
      )} ${percentage}%, transparent ${percentage}%)`;
    } else {
      node.style.background = '';
    }

    let outputString = `${resultNumber} (${percentage}%)`;
    if (hideAbsoluteValue && hideRelativeValue) {
      outputString = '';
    } else if (hideAbsoluteValue) {
      outputString = percentage + '%';
    } else if (hideRelativeValue) {
      outputString = resultNumber;
    }

    return (
      <DmnJsPortal key={ruleId} renderIn={entryPoints.rules[ruleId]}>
        <i>{outputString}</i>
      </DmnJsPortal>
    );
  };

  const {rules, summary} = entryPoints;
  const hitCount = data.map(({value}) => value).reduce((sum, current) => sum + current, 0);

  const hitsColumn = useMemo(createHitsColumnAddon, []);
  const additionalModules = useMemo(() => [hitsColumn.Addon], [hitsColumn.Addon]);
  const onLoad = useCallback(() => {
    setEntryPoints(hitsColumn.entryPoints);
  }, [hitsColumn.entryPoints]);

  return (
    <div className="DecisionTable">
      <DMNDiagram
        xml={xml}
        decisionDefinitionKey={key}
        additionalModules={additionalModules}
        onLoad={onLoad}
      >
        {Object.keys(rules).map(renderRuleCell)}
        <DmnJsPortal renderIn={summary}>
          <b>
            {instanceCount} Evaluation{instanceCount !== 1 ? 's' : ''}
            {hitCount > instanceCount && ` / ${hitCount} Hits`}
          </b>
        </DmnJsPortal>
      </DMNDiagram>
    </div>
  );
}

export default themed(DecisionTable);

function getColor(theme, progress) {
  if (theme === 'light') {
    return `hsl(223, 100%, ${90 - progress * 30}%)`;
  } else {
    return `hsl(223, 100%, ${30 + progress * 30}%)`;
  }
}
