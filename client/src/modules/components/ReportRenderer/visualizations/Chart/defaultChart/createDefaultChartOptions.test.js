import {createDatasetOptions} from './createDefaultChartOptions';

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
