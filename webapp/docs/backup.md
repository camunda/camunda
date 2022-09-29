### Data backup

Tasklist stores its data over multiple indices in Elasticsearch. Data backup happens as the series of Elasticsearch
snapshots for single indices or groups of indices. Different Tasklist modules and processes put limitations
on backup process. Importer and archiver define the order in which snapshot of different indices should happen.
All indices that store some kind of state should be backed up before all dependent indices. E.g. in case of importer
snapshot of the tasklist-import-position index that stores import progress should be taken earlier than snapshots of all
indices that store imported data.

Currently order of index snapshotting look like this:

| Order | Priority | Index/indices                                                                   |
|-------|----------|---------------------------------------------------------------------------------|
| 1     | Prio 1   | import-position index                                                           |
| 2     | Prio 2   | process-instance index and main task index                                      |
| 3     | Prio 2+  | All dated task_<date> indices                                                   |
| 4     | Prio 3   | Main task-variable index, variable and flownode-instance indices                |
| 5     | Prio 3+  | Dated task-variable indices                                                     |
| 6     | Prio 4   | All other indices: migration-steps-repository, process, form, user, metric etc. |
