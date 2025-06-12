package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.JobEntity;
import java.util.function.Function;

public enum JobSearchColumn implements SearchColumn<JobEntity> {
  JOB_KEY("jobKey", JobEntity::jobKey),
  TYPE("type", JobEntity::type),
  WORKER("worker", JobEntity::worker),
  STATE("state", JobEntity::state),
  KIND("kind", JobEntity::kind),
  LISTENER_EVENT_TYPE("listenerEventType", JobEntity::listenerEventType),
  PROCESS_DEFINITION_ID("processDefinitionId", JobEntity::processDefinitionId),
  PROCESS_DEFINITION_KEY("processDefinitionKey", JobEntity::processDefinitionKey),
  PROCESS_INSTANCE_KEY("processInstanceKey", JobEntity::processInstanceKey),
  ELEMENT_ID("elementId", JobEntity::elementId),
  ELEMENT_INSTANCE_KEY("elementInstanceKey", JobEntity::elementInstanceKey),
  TENANT_ID("tenantId", JobEntity::tenantId);

  private final String property;
  private final Function<JobEntity, Object> propertyReader;
  private final Function<Object, Object> sortOptionConverter;

  JobSearchColumn(final String property, final Function<JobEntity, Object> propertyReader) {
    this(property, propertyReader, Function.identity());
  }

  JobSearchColumn(
      final String property,
      final Function<JobEntity, Object> propertyReader,
      final Function<Object, Object> sortOptionConverter) {
    this.property = property;
    this.propertyReader = propertyReader;
    this.sortOptionConverter = sortOptionConverter;
  }

  @Override
  public Object getPropertyValue(final JobEntity entity) {
    return propertyReader.apply(entity);
  }

  @Override
  public Object convertSortOption(final Object object) {
    if (object == null) {
      return null;
    }
    return sortOptionConverter.apply(object);
  }

  public static JobSearchColumn findByProperty(final String property) {
    for (final JobSearchColumn column : JobSearchColumn.values()) {
      if (column.property.equals(property)) {
        return column;
      }
    }
    return null;
  }
}
