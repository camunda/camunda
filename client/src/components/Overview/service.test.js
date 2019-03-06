import {getEntitiesCollections} from './service';

const collection = {
  id: 'aCollectionId',
  name: 'aCollectionName',
  created: '2017-11-11T11:11:11.1111+0200',
  owner: 'user_id',
  data: {
    configuration: {},
    entities: [
      {
        id: 'reportID1'
      },
      {
        id: 'reportID2'
      }
    ]
  }
};

it('should return entities Collections as a hash of arrays', () => {
  expect(getEntitiesCollections([collection])).toEqual({
    reportID1: [collection],
    reportID2: [collection]
  });
});
