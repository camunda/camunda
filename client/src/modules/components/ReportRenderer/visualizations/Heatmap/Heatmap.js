import React from 'react';

import {BPMNDiagram, TargetValueBadge, LoadingIndicator} from 'components';
import HeatmapOverlay from './HeatmapOverlay';

import {calculateTargetValueHeat} from './service';
import {getRelativeValue} from '../service';
import {formatters} from 'services';

import './Heatmap.scss';

const Heatmap = ({report, formatter, errorMessage}) => {
  const {
    result,
    data: {
      configuration: {alwaysShowAbsolute, alwaysShowRelative, heatmapTargetValue: targetValue, xml},
      view
    },
    processInstanceCount
  } = report;

  if (!result || typeof result !== 'object') {
    return <p>{errorMessage}</p>;
  }

  if (!xml) {
    return <LoadingIndicator />;
  }

  let heatmapComponent;
  if (targetValue && targetValue.active && !targetValue.values.target) {
    const heat = calculateTargetValueHeat(result, targetValue.values);
    heatmapComponent = [
      <HeatmapOverlay
        key="heatmap"
        data={heat}
        alwaysShowAbsolute={alwaysShowAbsolute}
        alwaysShowRelative={alwaysShowRelative}
        formatter={(_, id) => {
          const node = document.createElement('div');

          const target = formatters.convertToMilliseconds(
            targetValue.values[id].value,
            targetValue.values[id].unit
          );
          const real = result[id];

          node.innerHTML = `target duration: ${formatters.duration(target)}<br/>`;

          if (typeof real === 'number') {
            const relation = (real / target) * 100;

            node.innerHTML += `actual duration: ${formatters.duration(real)}<br/>${
              relation < 1 ? '< 1' : Math.round(relation)
            }% of the target value`;
          } else {
            node.innerHTML += `No actual value available.<br/>Cannot compare target and actual value.`;
          }

          // tooltips don't work well with spaces
          node.innerHTML = node.innerHTML.replace(/ /g, '\u00A0');

          return node;
        }}
      />,
      <TargetValueBadge key="targetValueBadge" values={targetValue.values} />
    ];
  } else {
    heatmapComponent = (
      <HeatmapOverlay
        data={result}
        alwaysShowAbsolute={alwaysShowAbsolute}
        alwaysShowRelative={alwaysShowRelative}
        formatter={data => {
          const absolute = formatter(data);
          const relative = getRelativeValue(data, processInstanceCount);

          if (view.property === 'duration') {
            return absolute;
          }

          if (alwaysShowAbsolute && alwaysShowRelative) {
            return absolute + `\u00A0(${relative})`;
          }

          if (alwaysShowAbsolute) {
            return absolute;
          }
          if (alwaysShowRelative) {
            return relative;
          }

          return absolute + `\u00A0(${relative})`;
        }}
      />
    );
  }

  return (
    <div className="Heatmap">
      <BPMNDiagram xml={xml}>{heatmapComponent}</BPMNDiagram>
    </div>
  );
};

export default Heatmap;
