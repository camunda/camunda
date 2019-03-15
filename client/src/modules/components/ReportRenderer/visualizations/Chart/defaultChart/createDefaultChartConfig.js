import createDefaultChartData from './createDefaultChartData';
import createDefaultChartOptions from './createDefaultChartOptions';
import createPlugins from '../createPlugins';

export default function createDefaultChartConfig(props) {
  const {
    data: {visualization}
  } = props.report;

  return {
    type: visualization,
    data: createDefaultChartData(props),
    options: createDefaultChartOptions(props),
    plugins: createPlugins(props)
  };
}
