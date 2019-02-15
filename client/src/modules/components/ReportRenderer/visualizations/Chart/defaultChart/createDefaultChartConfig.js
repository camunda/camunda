import {drawHorizentalLine} from '../service';
import createDefaultChartData from './createDefaultChartData';
import createDefaultChartOptions from './createDefaultChartOptions';

export default function createDefaultChartConfig(props) {
  const {
    data: {visualization}
  } = props.report;

  return {
    type: visualization,
    data: createDefaultChartData(props),
    options: createDefaultChartOptions(props),
    plugins: [
      {
        afterDatasetsDraw: drawHorizentalLine
      }
    ]
  };
}
