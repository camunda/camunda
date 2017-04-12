import {withSelector, DESTROY_EVENT, noop} from 'view-utils';
import {getChartDimensions, createScales, createAxes, createTooltip, createChartOn, createContainer,
        updateScales, updateAxes, collectBars, updateBars, createNewBars, removeOldBars} from 'utils/chart-utils';

export const Chart = withSelector(({config}) => {
  return (parentNode, eventsBus) => {
    const svg = createChartOn(parentNode);
    const {margin, width, height} = getChartDimensions(svg, config);

    const container = createContainer(svg, margin);

    const {x, y} = createScales(width, height);
    const {xAxis, yAxis} = createAxes(container, height);

    const tooltip = createTooltip(svg);

    eventsBus.on(DESTROY_EVENT, () => {
      tooltip.destroy();
    });

    return (data) => {
      updateScales({data, x, y});
      updateAxes({xAxis, yAxis, x, y, scale: config.absoluteScale ? 'd' : '%', width});

      const bars = collectBars({container, data});

      updateBars({bars, x, y, height});
      createNewBars({bars, x, y, height, tooltip, onHoverChange: config.onHoverChange || noop});
      removeOldBars(bars);
    };
  };
});
