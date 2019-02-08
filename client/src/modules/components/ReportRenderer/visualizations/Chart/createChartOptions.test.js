import {createDatasetOptions, getTargetLineOptions} from './createChartOptions';

it('should create dataset option for barchart report', () => {
  const data = {foo: 123, bar: 5};
  const options = createDatasetOptions('bar', data, false, 'testColor', false, false);
  expect(options).toEqual({
    backgroundColor: 'testColor',
    borderColor: 'testColor',
    borderWidth: 1,
    legendColor: 'testColor'
  });
});

it('should create dataset option for pie reports', () => {
  const data = {foo: 123, bar: 5};
  const options = createDatasetOptions('pie', data, false, 'testColor', false, false);
  expect(options).toEqual({
    backgroundColor: ['hsl(50, 65%, 50%)', 'hsl(180, 65%, 50%)'],
    borderColor: '#fff',
    borderWidth: undefined
  });
});

it('should should return correct option for line chart with target value', () => {
  const options = getTargetLineOptions('testColor', true, true, true);
  expect(options).toEqual({
    normalLineOptions: {
      backgroundColor: 'transparent',
      borderColor: 'testColor',
      borderWidth: 2,
      legendColor: 'testColor',
      renderArea: 'top'
    },
    targetOptions: {
      backgroundColor: 'transparent',
      borderColor: 'testColor',
      borderWidth: 2,
      legendColor: 'testColor',
      pointBorderColor: '#A62A31',
      renderArea: 'bottom'
    }
  });
});
