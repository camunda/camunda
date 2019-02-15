import {getTargetLineOptions} from './createTargetLineOptions';

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
