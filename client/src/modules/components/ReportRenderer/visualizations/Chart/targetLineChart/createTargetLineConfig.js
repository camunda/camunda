import {drawHorizentalLine} from '../service';
import createTargetLineData from './createTargetLineData';
import createTargetLineOptions from './createTargetLineOptions';
import './addChartType';

export default function createTargetLineConfig(props) {
  return {
    type: 'targetLine',
    data: createTargetLineData(props),
    options: createTargetLineOptions(props),
    plugins: [
      {
        afterDatasetsDraw: drawHorizentalLine
      }
    ]
  };
}
