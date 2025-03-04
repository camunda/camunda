package io.camunda.db.rdbms.read.mapper;

import io.camunda.db.rdbms.write.domain.BatchOperationDbModel;
import io.camunda.search.entities.BatchOperationEntity;

public class BatchOperationEntityMapper {

  public static BatchOperationEntity toEntity(final BatchOperationDbModel dbModel) {
    return new BatchOperationEntity(
        dbModel.batchOperationKey(),
        dbModel.state(),
        dbModel.operationType(),
        dbModel.startDate(),
        dbModel.endDate(),
        dbModel.operationsTotalCount(),
        dbModel.operationsFailedCount(),
        dbModel.operationsCompletedCount()
    );
  }

}
