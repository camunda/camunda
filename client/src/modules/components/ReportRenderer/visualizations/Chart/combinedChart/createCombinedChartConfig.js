import {drawHorizentalLine} from '../service';
import createCombinedChartData from './createCombinedChartData';
import createCombinedChartOptions from './createCombinedChartOptions';

export default function createCombinedChartConfig(props) {
  const {visualization} = props.report.data;
  const chartVisualization = visualization === 'number' ? 'bar' : visualization;

  return {
    type: chartVisualization,
    data: createCombinedChartData(props),
    options: createCombinedChartOptions(props),
    plugins: [
      {
        afterDatasetsDraw: drawHorizentalLine
      }
    ]
  };
}
