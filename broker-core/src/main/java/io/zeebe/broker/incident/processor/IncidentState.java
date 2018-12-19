/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.incident.processor;

import io.zeebe.broker.logstreams.state.UnpackedObjectValue;
import io.zeebe.broker.logstreams.state.ZbColumnFamilies;
import io.zeebe.db.ColumnFamily;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbLong;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import java.util.function.ObjLongConsumer;

public class IncidentState {
  public static final int MISSING_INCIDENT = -1;

  private final ZeebeDb zeebeDb;

  /** incident key -> incident record */
  private final DbLong incidentKey;

  private final UnpackedObjectValue incident;
  private final ColumnFamily<DbLong, UnpackedObjectValue> incidentColumnFamily;

  /** element instance key -> incident key */
  private final DbLong elementInstanceKey;

  private final ColumnFamily<DbLong, DbLong> workflowInstanceIncidentColumnFamily;

  /** job key -> incident key */
  private final DbLong jobKey;

  private final ColumnFamily<DbLong, DbLong> jobIncidentColumnFamily;

  public IncidentState(ZeebeDb<ZbColumnFamilies> zeebeDb) {
    this.zeebeDb = zeebeDb;

    incidentKey = new DbLong();
    incident = new UnpackedObjectValue();
    incident.wrapObject(new IncidentRecord());
    incidentColumnFamily =
        zeebeDb.createColumnFamily(ZbColumnFamilies.INCIDENTS, incidentKey, incident);

    elementInstanceKey = new DbLong();
    workflowInstanceIncidentColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.INCIDENT_WORKFLOW_INSTANCES, elementInstanceKey, incidentKey);

    jobKey = new DbLong();
    jobIncidentColumnFamily =
        zeebeDb.createColumnFamily(ZbColumnFamilies.INCIDENT_JOBS, jobKey, incidentKey);
  }

  public void createIncident(long incidentKey, IncidentRecord incident) {
    zeebeDb.batch(
        () -> {
          this.incidentKey.wrapLong(incidentKey);
          this.incident.wrapObject(incident);

          incidentColumnFamily.put(this.incidentKey, this.incident);

          if (isJobIncident(incident)) {
            jobKey.wrapLong(incident.getJobKey());
            jobIncidentColumnFamily.put(jobKey, this.incidentKey);
          } else {
            elementInstanceKey.wrapLong(incident.getElementInstanceKey());
            workflowInstanceIncidentColumnFamily.put(elementInstanceKey, this.incidentKey);
          }
        });
  }

  public IncidentRecord getIncidentRecord(long incidentKey) {
    this.incidentKey.wrapLong(incidentKey);

    final UnpackedObjectValue unpackedObjectValue = incidentColumnFamily.get(this.incidentKey);
    if (unpackedObjectValue != null) {
      return (IncidentRecord) unpackedObjectValue.getObject();
    }
    return null;
  }

  public void deleteIncident(long key) {
    final IncidentRecord incidentRecord = getIncidentRecord(key);

    if (incidentRecord != null) {
      zeebeDb.batch(
          () -> {
            incidentColumnFamily.delete(incidentKey);

            if (isJobIncident(incidentRecord)) {
              jobKey.wrapLong(incidentRecord.getJobKey());
              jobIncidentColumnFamily.delete(jobKey);
            } else {
              elementInstanceKey.wrapLong(incidentRecord.getElementInstanceKey());
              workflowInstanceIncidentColumnFamily.delete(elementInstanceKey);
            }
          });
    }
  }

  public long getWorkflowInstanceIncidentKey(long instanceKey) {
    elementInstanceKey.wrapLong(instanceKey);

    final DbLong incidentKey = workflowInstanceIncidentColumnFamily.get(elementInstanceKey);

    if (incidentKey != null) {
      return incidentKey.getValue();
    }

    return MISSING_INCIDENT;
  }

  public long getJobIncidentKey(long jobKey) {
    this.jobKey.wrapLong(jobKey);
    final DbLong incidentKey = jobIncidentColumnFamily.get(this.jobKey);

    if (incidentKey != null) {
      return incidentKey.getValue();
    }
    return MISSING_INCIDENT;
  }

  public boolean isJobIncident(IncidentRecord record) {
    return record.getJobKey() > 0;
  }

  public void forExistingWorkflowIncident(
      long elementInstanceKey, ObjLongConsumer<IncidentRecord> resolver) {
    final long workflowIncidentKey = getWorkflowInstanceIncidentKey(elementInstanceKey);

    final boolean hasIncident = workflowIncidentKey != IncidentState.MISSING_INCIDENT;
    if (hasIncident) {
      final IncidentRecord incidentRecord = getIncidentRecord(workflowIncidentKey);
      resolver.accept(incidentRecord, workflowIncidentKey);
    }
  }
}
