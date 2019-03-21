import {processResult} from './service';

it('should process duration reports', () => {
  expect(
    processResult({
      resultType: 'durationMap',
      data: {
        view: {
          property: 'duration',
          entity: 'processInstance'
        },
        configuration: {aggregationType: 'max'}
      },
      result: {
        '2015-03-25T12:00:00Z': {min: 1, median: 2, avg: 3, max: 4},
        '2015-03-26T12:00:00Z': {min: 5, median: 6, avg: 7, max: 8}
      }
    })
  ).toEqual({
    '2015-03-25T12:00:00Z': 4,
    '2015-03-26T12:00:00Z': 8
  });
});
