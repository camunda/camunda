import {drawHorizentalLine} from '../service';
import createChartData from '../createChartData';
import createChartOptions from '../createChartOptions';

export default function createCombinedChartConfig(props) {
  const {visualization} = props.report.data;
  const chartVisualization = visualization === 'number' ? 'bar' : visualization;

  return {
    type: chartVisualization,
    data: createChartData(props),
    options: createChartOptions(props),
    plugins: [
      {
        afterDatasetsDraw: drawHorizentalLine
      }
    ]
  };
}
