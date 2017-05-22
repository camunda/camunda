import {dispatchAction, $document} from 'view-utils';
import {createSetTargetValueAction} from './reducer';
import {formatNumber, createDelayedTimePrecisionElement} from 'utils';
import {put} from 'http';
import {addNotification} from 'notifications';
import * as timeUtil from 'utils/formatTime';
import {addDiagramTooltip} from '../service';

const TARGET_VALUE_BADGE = 'TARGET_VALUE_BADGE';

const formatTime = timeUtil.formatTime;

export function setTargetValue(element, value) {
  dispatchAction(createSetTargetValueAction(element, value));
}

export function getTargetValue(State, {businessObject:{id}}) {
  return State.getState().targetValue.data[id];
}

export function getTargetDurationFromForm(form) {
  return ['ms', 's', 'm', 'h', 'd', 'w']
    .map(unit => parseInt(form.querySelector('[for="'+unit+'"]').value, 10) * timeUtil[unit])
    .reduce((prev, curr) => prev + curr, 0);
}

export function setTargetDurationToForm(form, targetValue) {
  const timeParts = formatTime(targetValue, {returnRaw: true});
  const fields = form.querySelectorAll('input');

  for (let i = 0; i < fields.length; i++) {
    const field = fields[i];
    const preset = timeParts.filter(el => el.name === field.getAttribute('for'))[0];

    field.value = preset && preset.howMuch || 0;
  }
}

export function saveTargetValues(processDefinitionId, targetValues) {
  put('/api/process-definition/heatmap/duration/target-value', {
    processDefinitionId,
    targetValues
  }).catch(err => {
    addNotification({
      status: 'Could not store target value data',
      isError: true
    });
  });
}

export function prepareFlowNodes(targetValues, actualValues) {
  return Object.keys(targetValues).reduce((processed, key) => {
    if (actualValues[key] > targetValues[key]) {
      processed[key] = actualValues[key] / targetValues[key] - 1;
    }
    return processed;
  }, {});
}

export function addTargetValueBadge(viewer, element, value, onClick) {
  const overlays = viewer.get('overlays');
  const container = document.createElement('div');

  container.innerHTML = `<span class="badge" style="cursor: pointer;">${formatTime(value)}</span>`;
  const overlayHtml = container.firstChild;

  // calculate overlay width
  document.body.appendChild(overlayHtml);
  const overlayWidth = overlayHtml.clientWidth;

  document.body.removeChild(overlayHtml);

  overlayHtml.addEventListener('click', () => {
    onClick(element);
  });

  // add overlay to viewer
  overlays.add(element.id, TARGET_VALUE_BADGE, {
    position: {
      top: -14,
      right: overlayWidth - 11
    },
    show: {
      minZoom: -Infinity,
      maxZoom: +Infinity
    },
    html: overlayHtml
  });
}

export function addTargetValueTooltip(viewer, element, actualValue, targetValue) {
  // create overlay node from html string
  const container = document.createElement('div');
  const targetValueElement = createDelayedTimePrecisionElement(targetValue, {initialPrecision: 2, delay: 1500});
  let actualValueElement;
  let percentage;

  if (typeof actualValue !== 'undefined') {
    actualValueElement = createDelayedTimePrecisionElement(actualValue, {initialPrecision: 2, delay: 1500});
    percentage = targetValue > actualValue ?
      `${formatNumber(Math.round((-actualValue / targetValue + 1) * 100))}%&nbsp;below&nbsp;target` :
      `${formatNumber(Math.round((actualValue / targetValue - 1) * 100))}%&nbsp;above&nbsp;target`;
  } else {
    actualValueElement = $document.createTextNode('no\xa0data');
    percentage = '';
  }

  container.innerHTML = `<span>target:&nbsp;<span class="target"></span><br />actual:&nbsp;<span class="actual"></span><br />${percentage}</span>`;
  const overlayHtml = container.firstChild;

  overlayHtml.querySelector('.target').appendChild(targetValueElement);
  overlayHtml.querySelector('.actual').appendChild(actualValueElement);

  return addDiagramTooltip(viewer, element.id, overlayHtml);
}
