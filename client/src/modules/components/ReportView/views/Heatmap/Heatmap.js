import React from 'react';

import {BPMNDiagram, TargetValueBadge, LoadingIndicator} from 'components';
import HeatmapOverlay from './HeatmapOverlay';

import {calculateTargetValueHeat} from './service';
import {getRelativeValue} from '../service';
import {formatters} from 'services';

import './Heatmap.scss';

const Heatmap = props => {
  const {xml} = props;
  const {data, errorMessage, targetValue} = props;

  if (!data || typeof data !== 'object') {
    return <p>{errorMessage}</p>;
  }

  if (!xml) {
    return <LoadingIndicator />;
  }

  let heatmapComponent;
  if (targetValue && targetValue.active && !targetValue.values.target) {
    const heat = calculateTargetValueHeat(data, targetValue.values);
    heatmapComponent = [
      <HeatmapOverlay
        key="heatmap"
        data={heat}
        hideAbsoluteValue={props.hideAbsoluteValue}
        hideRelativeValue={props.hideRelativeValue}
        formatter={(_, id) => {
          const node = document.createElement('div');

          const target = formatters.convertToMilliseconds(
            targetValue.values[id].value,
            targetValue.values[id].unit
          );
          const real = data[id];

          node.innerHTML = `target duration: ${formatters.duration(target)}<br/>`;

          if (typeof real === 'number') {
            const relation = real / target * 100;

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
        data={data}
        hideAbsoluteValue={props.hideAbsoluteValue}
        hideRelativeValue={props.hideRelativeValue}
        formatter={data => {
          const allDisabled =
            (props.hideAbsoluteValue && props.hideRelativeValue) ||
            (typeof props.hideAbsoluteValue === 'undefined' &&
              typeof props.hideRelativeValue === 'undefined');

          const allEnabled = props.hideAbsoluteValue === false && props.hideRelativeValue === false;
          const relativeOnly = props.hideRelativeValue === false && props.hideAbsoluteValue;

          let formattedValue = props.formatter(data);

          if (props.property === 'frequency' && (relativeOnly || allDisabled || allEnabled)) {
            const relativeValue = getRelativeValue(data, props.processInstanceCount);
            if (relativeOnly) formattedValue = relativeValue;
            else formattedValue += `\u00A0(${relativeValue})`;
          }

          return formattedValue;
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
