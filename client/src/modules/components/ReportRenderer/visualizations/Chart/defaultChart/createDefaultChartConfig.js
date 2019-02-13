import {drawHorizentalLine} from '../service';
import createChartData from '../createChartData';
import createChartOptions from '../createChartOptions';

export default function createDefaultChartConfig(props) {
  const {
    data: {visualization}
  } = props.report;

  return {
    type: visualization,
    data: createChartData(props),
    options: createChartOptions(props),
    plugins: [
      {
        afterDatasetsDraw: drawHorizentalLine
      }
    ]
  };
}
