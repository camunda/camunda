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

import static io.zeebe.logstreams.rocksdb.ZeebeStateConstants.STATE_BYTE_ORDER;

import io.zeebe.broker.incident.data.IncidentRecord;
import io.zeebe.broker.workflow.state.PersistenceHelper;
import io.zeebe.logstreams.rocksdb.ZbRocksDb;
import io.zeebe.logstreams.state.StateController;
import io.zeebe.logstreams.state.StateLifecycleListener;
import java.util.List;
import java.util.function.ObjLongConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.ColumnFamilyHandle;

public class IncidentState implements StateLifecycleListener {

  private static final byte[] INCIDENT_COLUMN_FAMILY_NAME = "incidentStateIncident".getBytes();
  private static final byte[] WORKFLOW_INSTANCE_INCIDENT_COLUMN_FAMILY_NAME =
      "incidentStateWorkflowInstanceIncident".getBytes();
  private static final byte[] JOB_INCIDENT_COLUMN_FAMILY_NAME =
      "incidentStateJobIncident".getBytes();

  private static final byte[][] COLUMN_FAMILY_NAMES = {
    INCIDENT_COLUMN_FAMILY_NAME,
    WORKFLOW_INSTANCE_INCIDENT_COLUMN_FAMILY_NAME,
    JOB_INCIDENT_COLUMN_FAMILY_NAME
  };
  public static final int MISSING_INCIDENT = -1;

  private ColumnFamilyHandle incidentColumnFamily;
  private ColumnFamilyHandle workflowInstanceIncidentColumnFamily;

  private ColumnFamilyHandle jobIncidentColumnFamily;
  private ZbRocksDb db;

  private final MutableDirectBuffer keyBuffer = new UnsafeBuffer(new byte[Long.BYTES]);
  private final MutableDirectBuffer valueBuffer = new ExpandableArrayBuffer();

  private final IncidentRecord incidentRecord = new IncidentRecord();
  private PersistenceHelper persistenceHelper;

  public static List<byte[]> getColumnFamilyNames() {
    return Stream.of(COLUMN_FAMILY_NAMES).flatMap(Stream::of).collect(Collectors.toList());
  }

  @Override
  public void onOpened(StateController stateController) {
    db = stateController.getDb();

    persistenceHelper = new PersistenceHelper(stateController);
    incidentColumnFamily = stateController.getColumnFamilyHandle(INCIDENT_COLUMN_FAMILY_NAME);
    workflowInstanceIncidentColumnFamily =
        stateController.getColumnFamilyHandle(WORKFLOW_INSTANCE_INCIDENT_COLUMN_FAMILY_NAME);
    jobIncidentColumnFamily =
        stateController.getColumnFamilyHandle(JOB_INCIDENT_COLUMN_FAMILY_NAME);
  }

  public void createIncident(long incidentKey, IncidentRecord incident) {
    keyBuffer.putLong(0, incidentKey, STATE_BYTE_ORDER);

    final int length = incident.getLength();
    incident.write(valueBuffer, 0);

    db.batch(
        batchWriter -> {
          batchWriter.put(
              incidentColumnFamily,
              keyBuffer.byteArray(),
              Long.BYTES,
              valueBuffer.byteArray(),
              length);

          valueBuffer.putLong(0, incidentKey, STATE_BYTE_ORDER);
          if (isJobIncident(incident)) {
            keyBuffer.putLong(0, incident.getJobKey(), STATE_BYTE_ORDER);
            batchWriter.put(
                jobIncidentColumnFamily,
                keyBuffer.byteArray(),
                Long.BYTES,
                valueBuffer.byteArray(),
                Long.BYTES);
          } else {
            keyBuffer.putLong(0, incident.getElementInstanceKey(), STATE_BYTE_ORDER);
            batchWriter.put(
                workflowInstanceIncidentColumnFamily,
                keyBuffer.byteArray(),
                Long.BYTES,
                valueBuffer.byteArray(),
                Long.BYTES);
          }
        });
  }

  public IncidentRecord getIncidentRecord(long incidentKey) {
    final boolean successfulRead =
        persistenceHelper.readInto(incidentRecord, incidentColumnFamily, incidentKey);

    return successfulRead ? incidentRecord : null;
  }

  public void deleteIncident(long key) {

    final IncidentRecord incidentRecord = getIncidentRecord(key);

    db.batch(
        batchWriter -> {
          if (incidentRecord != null) {
            keyBuffer.putLong(0, key, STATE_BYTE_ORDER);
            batchWriter.delete(incidentColumnFamily, keyBuffer.byteArray(), Long.BYTES);

            if (isJobIncident(incidentRecord)) {
              keyBuffer.putLong(0, incidentRecord.getJobKey(), STATE_BYTE_ORDER);
              batchWriter.delete(jobIncidentColumnFamily, keyBuffer.byteArray(), Long.BYTES);
            } else {
              final long elementInstanceKey = incidentRecord.getElementInstanceKey();
              keyBuffer.putLong(0, elementInstanceKey, STATE_BYTE_ORDER);

              batchWriter.delete(
                  workflowInstanceIncidentColumnFamily, keyBuffer.byteArray(), Long.BYTES);
            }
          }
        });
  }

  public long getWorkflowInstanceIncidentKey(long instanceKey) {
    final int readBytes = db.get(workflowInstanceIncidentColumnFamily, instanceKey, valueBuffer);

    if (readBytes > 0) {
      final long incidentKey = valueBuffer.getLong(0, STATE_BYTE_ORDER);
      return incidentKey;
    }
    return MISSING_INCIDENT;
  }

  public long getJobIncidentKey(long jobKey) {
    final int readBytes = db.get(jobIncidentColumnFamily, jobKey, valueBuffer);

    if (readBytes > 0) {
      final long incidentKey = valueBuffer.getLong(0, STATE_BYTE_ORDER);
      return incidentKey;
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
