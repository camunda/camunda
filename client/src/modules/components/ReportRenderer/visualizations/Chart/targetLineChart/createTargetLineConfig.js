import {drawHorizentalLine} from '../service';
import createChartData from '../createChartData';
import createChartOptions from '../createChartOptions';
import './addChartType';

export default function createTargetLineConfig(props) {
  return {
    type: 'targetLine',
    data: createChartData(props),
    options: createChartOptions(props),
    plugins: [
      {
        afterDatasetsDraw: drawHorizentalLine
      }
    ]
  };
}
