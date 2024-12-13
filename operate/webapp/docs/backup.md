### Data backup

Operate stores its data over multiple indices in Elasticsearch. Data backup happens as the series of Elasticsearch
snapshots for single indices or groups of indices. Different Operate modules and processes put limitations
on backup process. Importer and post importer define the order in which snapshot of different indices should happen.
All indices that store some kind of state should be backed up before all dependent indices. E.g. in case of importer
snapshot of the operate-import-position index that stores import progress should be taken earlier than snapshots of all
indices that store imported data.

Currently order of index snapshotting look like this:

| Order | Priority |                                                      Index/indices                                                       |
|-------|----------|--------------------------------------------------------------------------------------------------------------------------|
| 1     | Prio 1   | import-position index                                                                                                    |
| 2     | Prio 2   | Main list-view index                                                                                                     |
| 3     | Prio 2+  | All dated list-view_<date> indices                                                                                       |
| 4     | Prio 3   | Main indices decision-instance, event, flow-node-instance, incident, sequence-flow, variable, operation, batch-operation |
| 5     | Prio 3+  | Dated indices for those from Prio 3 (e.g. decision-instance_<date>, event_<date> etc.)                                   |
| 6     | Prio 4   | All other indices: migration-steps-repository, process, decision, decision-requirements, metric etc.                     |

