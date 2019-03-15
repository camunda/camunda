import createTargetLineData from './createTargetLineData';
import createTargetLineOptions from './createTargetLineOptions';
import createPlugins from '../createPlugins';
import './addChartType';

export default function createTargetLineConfig(props) {
  return {
    type: 'targetLine',
    data: createTargetLineData(props),
    options: createTargetLineOptions(props),
    plugins: createPlugins(props)
  };
}
