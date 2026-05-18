# OC API v2 — Coverage matrix (entity × operation × variant)

Total test declarations: **1001** across **33** entities.

Variants are first-match labels from test names; one test can carry multiple labels (sum may exceed row total).

Legend: ✓ = at least 1, blank = 0.

## At-a-glance presence (✓ = ≥1 test)

| entity | op | total | happy | bad-req | 401 | 403 | 404 | conflict | pagin/sort | filter | absence |
|--|--|--:|--|--|--|--|--|--|--|--|--|
| process-instance | create | 43 | ✓ | ✓ | ✓ |  | ✓ |  |  | ✓ |  |
| process-instance | get | 24 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| process-instance | update | 16 | ✓ | ✓ | ✓ | ✓ | ✓ |  |  |  |  |
| process-instance | delete | 20 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |  | ✓ |  |
| process-instance | search | 34 | ✓ | ✓ | ✓ |  | ✓ |  | ✓ | ✓ |  |
| process-instance | other | 14 | ✓ | ✓ |  |  |  | ✓ |  |  | ✓ |
| tenant | create | 11 |  | ✓ | ✓ |  |  | ✓ |  |  |  |
| tenant | get | 5 |  |  | ✓ |  | ✓ |  |  |  |  |
| tenant | update | 26 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| tenant | delete | 23 | ✓ |  | ✓ |  | ✓ |  |  |  |  |
| tenant | search | 45 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| role | create | 10 |  | ✓ | ✓ |  |  | ✓ |  |  |  |
| role | get | 5 |  |  | ✓ |  | ✓ |  |  |  |  |
| role | update | 23 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| role | delete | 18 |  |  | ✓ |  | ✓ |  |  |  |  |
| role | search | 44 |  | ✓ | ✓ |  | ✓ |  |  | ✓ |  |
| authorization | create | 44 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |  |  |  |
| authorization | get | 8 | ✓ |  | ✓ | ✓ | ✓ |  |  |  |  |
| authorization | update | 12 | ✓ | ✓ | ✓ | ✓ | ✓ |  |  |  |  |
| authorization | delete | 7 | ✓ | ✓ | ✓ | ✓ | ✓ |  |  |  |  |
| authorization | search | 33 | ✓ | ✓ | ✓ | ✓ |  |  | ✓ | ✓ |  |
| cluster-variables | create | 11 |  | ✓ | ✓ |  | ✓ |  |  |  |  |
| cluster-variables | get | 10 |  |  | ✓ |  | ✓ |  |  |  |  |
| cluster-variables | update | 32 |  | ✓ | ✓ |  | ✓ |  |  |  |  |
| cluster-variables | delete | 6 |  |  | ✓ |  | ✓ |  |  |  |  |
| cluster-variables | search | 30 | ✓ | ✓ | ✓ |  |  |  | ✓ | ✓ |  |
| job | create | 1 |  |  |  |  |  |  |  |  |  |
| job | get | 14 | ✓ | ✓ | ✓ | ✓ |  |  |  |  |  |
| job | update | 22 | ✓ | ✓ |  | ✓ | ✓ | ✓ |  |  |  |
| job | search | 49 | ✓ | ✓ | ✓ | ✓ | ✓ |  | ✓ | ✓ |  |
| job | other | 11 |  | ✓ | ✓ |  |  |  |  |  |  |
| user-task | get | 16 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| user-task | update | 23 | ✓ | ✓ | ✓ |  | ✓ | ✓ |  |  |  |
| user-task | delete | 7 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| user-task | search | 26 | ✓ | ✓ | ✓ |  |  |  | ✓ | ✓ |  |
| group | create | 6 |  | ✓ | ✓ |  |  | ✓ |  |  |  |
| group | get | 5 |  |  | ✓ |  | ✓ |  |  |  |  |
| group | update | 8 |  | ✓ | ✓ |  | ✓ |  |  |  |  |
| group | delete | 12 |  |  | ✓ |  | ✓ |  |  |  |  |
| group | search | 29 |  | ✓ | ✓ |  | ✓ |  |  |  |  |
| element-instance | get | 8 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| element-instance | update | 2 |  |  |  |  |  |  |  |  |  |
| element-instance | search | 57 | ✓ | ✓ | ✓ | ✓ | ✓ |  | ✓ | ✓ |  |
| element-instance | other | 7 |  |  | ✓ |  | ✓ |  |  |  |  |
| decision-definition | get | 14 |  | ✓ | ✓ |  | ✓ |  |  |  |  |
| decision-definition | update | 9 |  | ✓ | ✓ |  |  |  |  |  |  |
| decision-definition | search | 24 |  | ✓ | ✓ |  |  |  | ✓ | ✓ |  |
| batch-operation | get | 6 | ✓ |  | ✓ |  | ✓ |  |  |  |  |
| batch-operation | update | 8 |  | ✓ |  |  |  |  |  |  |  |
| batch-operation | delete | 7 |  | ✓ |  |  |  |  |  |  |  |
| batch-operation | search | 37 | ✓ | ✓ | ✓ |  |  |  | ✓ | ✓ |  |
| user | create | 12 | ✓ | ✓ | ✓ |  |  | ✓ |  |  |  |
| user | get | 5 |  |  | ✓ |  | ✓ |  |  |  |  |
| user | update | 6 | ✓ |  | ✓ |  | ✓ |  |  |  |  |
| user | delete | 3 |  |  | ✓ |  | ✓ |  |  |  |  |
| user | search | 9 |  | ✓ | ✓ |  |  |  |  |  |  |
| global-task-listener | create | 11 | ✓ | ✓ | ✓ | ✓ |  | ✓ |  |  |  |
| global-task-listener | update | 7 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| global-task-listener | delete | 7 | ✓ |  | ✓ |  | ✓ | ✓ |  |  | ✓ |
| global-task-listener | search | 20 |  | ✓ | ✓ |  |  |  | ✓ |  |  |
| process-definition | get | 26 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| process-definition | search | 36 |  | ✓ | ✓ |  |  |  | ✓ | ✓ |  |
| incident | get | 8 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| incident | update | 24 | ✓ | ✓ | ✓ | ✓ | ✓ |  | ✓ | ✓ |  |
| incident | search | 28 | ✓ | ✓ | ✓ | ✓ |  |  | ✓ | ✓ |  |
| mapping-rule | create | 7 |  | ✓ | ✓ |  |  | ✓ |  |  |  |
| mapping-rule | get | 5 |  |  | ✓ |  | ✓ |  |  |  |  |
| mapping-rule | update | 6 |  | ✓ | ✓ |  | ✓ |  |  |  |  |
| mapping-rule | delete | 3 |  |  | ✓ |  | ✓ |  |  |  |  |
| mapping-rule | search | 9 |  |  | ✓ |  |  |  |  |  |  |
| document | create | 16 |  | ✓ | ✓ |  | ✓ |  |  |  |  |
| document | get | 6 |  |  | ✓ |  | ✓ |  |  |  |  |
| document | delete | 3 |  |  | ✓ |  | ✓ |  |  |  |  |
| decision-instance | create | 8 |  | ✓ | ✓ | ✓ | ✓ |  |  | ✓ |  |
| decision-instance | get | 8 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| decision-instance | delete | 5 | ✓ |  | ✓ | ✓ | ✓ |  |  |  |  |
| decision-instance | search | 18 | ✓ | ✓ | ✓ |  |  |  |  | ✓ |  |
| message | create | 4 |  | ✓ | ✓ |  |  |  |  |  |  |
| message | update | 5 |  | ✓ | ✓ |  | ✓ |  |  |  |  |
| message | search | 30 | ✓ | ✓ | ✓ | ✓ |  |  | ✓ | ✓ |  |
| resource | create | 8 | ✓ | ✓ | ✓ |  |  |  |  |  |  |
| resource | get | 12 | ✓ |  | ✓ |  | ✓ |  |  |  |  |
| resource | delete | 5 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| audit-log | get | 8 | ✓ |  | ✓ | ✓ | ✓ |  |  |  |  |
| audit-log | search | 29 | ✓ | ✓ | ✓ | ✓ |  |  | ✓ | ✓ |  |
| audit-log | parameterized | 2 |  |  |  |  |  |  |  |  |  |
| decision-requirements | get | 16 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| decision-requirements | search | 13 | ✓ | ✓ | ✓ |  |  |  |  | ✓ |  |
| variable | get | 8 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| variable | search | 23 | ✓ | ✓ | ✓ |  |  |  | ✓ | ✓ |  |
| conditional | update | 14 | ✓ | ✓ | ✓ |  |  |  |  |  |  |
| optimize | search | 1 |  |  |  |  |  |  |  | ✓ |  |
| optimize | other | 5 |  |  |  |  |  |  |  |  |  |
| signal | create | 5 | ✓ | ✓ | ✓ |  |  |  |  |  |  |
| authentication | get | 5 |  |  | ✓ |  |  |  |  |  |  |
| usage-metrics | get | 8 | ✓ | ✓ | ✓ | ✓ |  |  |  |  |  |
| cluster | get | 4 |  |  | ✓ |  |  |  |  |  |  |
| expression | parameterized | 3 |  |  |  |  |  |  |  |  |  |
| clock | create | 2 |  | ✓ |  |  |  |  |  |  |  |
| clock | delete | 1 |  |  |  |  |  |  |  |  |  |
| license | get | 3 |  |  | ✓ |  |  |  |  |  |  |
| message-subscriptions | search | 3 |  |  | ✓ |  |  |  |  |  |  |

## Counts per cell

| entity | op | total | happy | bad-req | 401 | 403 | 404 | conflict | pagin/sort | filter | absence |
|--|--|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|
| process-instance | create | 43 | 9 | 12 | 4 |  | 1 |  |  | 15 |  |
| process-instance | get | 24 | 3 | 3 | 3 |  | 1 |  |  |  |  |
| process-instance | update | 16 | 3 | 7 | 3 | 1 | 2 |  |  |  |  |
| process-instance | delete | 20 | 4 | 3 | 3 | 2 | 3 | 1 |  | 2 |  |
| process-instance | search | 34 | 5 | 3 | 3 |  | 1 |  | 1 | 2 |  |
| process-instance | other | 14 | 8 | 1 |  |  |  | 2 |  |  | 1 |
| tenant | create | 11 |  | 3 | 1 |  |  | 6 |  |  |  |
| tenant | get | 5 |  |  | 1 |  | 1 |  |  |  |  |
| tenant | update | 26 | 5 | 2 | 6 |  | 9 |  |  |  |  |
| tenant | delete | 23 | 2 |  | 6 |  | 11 |  |  |  |  |
| tenant | search | 45 | 2 | 7 | 6 |  | 3 |  |  |  |  |
| role | create | 10 |  | 3 | 1 |  |  | 5 |  |  |  |
| role | get | 5 |  |  | 1 |  | 1 |  |  |  |  |
| role | update | 23 | 2 | 2 | 5 |  | 7 |  |  |  |  |
| role | delete | 18 |  |  | 5 |  | 9 |  |  |  |  |
| role | search | 44 |  | 6 | 5 |  | 2 |  |  | 4 |  |
| authorization | create | 44 | 10 | 15 | 5 | 5 | 4 | 5 |  |  |  |
| authorization | get | 8 | 1 |  | 1 | 1 | 1 |  |  |  |  |
| authorization | update | 12 | 5 | 3 | 1 | 1 | 2 |  |  |  |  |
| authorization | delete | 7 | 3 | 1 | 1 | 1 | 1 |  |  |  |  |
| authorization | search | 33 | 6 | 4 | 1 | 1 |  |  | 4 | 5 |  |
| cluster-variables | create | 11 |  | 5 | 2 |  | 1 |  |  |  |  |
| cluster-variables | get | 10 |  |  | 2 |  | 2 |  |  |  |  |
| cluster-variables | update | 32 |  | 7 | 2 |  | 3 |  |  |  |  |
| cluster-variables | delete | 6 |  |  | 2 |  | 2 |  |  |  |  |
| cluster-variables | search | 30 | 1 | 3 | 1 |  |  |  | 4 | 6 |  |
| job | create | 1 |  |  |  |  |  |  |  |  |  |
| job | get | 14 | 3 | 3 | 1 | 1 |  |  |  |  |  |
| job | update | 22 | 5 | 6 |  | 1 | 4 | 2 |  |  |  |
| job | search | 49 | 6 | 11 | 4 | 3 | 1 |  | 1 | 2 |  |
| job | other | 11 |  | 3 | 1 |  |  |  |  |  |  |
| user-task | get | 16 | 3 | 1 | 2 |  | 2 |  |  |  |  |
| user-task | update | 23 | 10 | 5 | 3 |  | 3 | 1 |  |  |  |
| user-task | delete | 7 | 4 | 1 | 1 |  | 1 |  |  |  |  |
| user-task | search | 26 | 3 | 3 | 2 |  |  |  | 1 | 4 |  |
| group | create | 6 |  | 1 | 1 |  |  | 3 |  |  |  |
| group | get | 5 |  |  | 1 |  | 1 |  |  |  |  |
| group | update | 8 |  | 1 | 1 |  | 2 |  |  |  |  |
| group | delete | 12 |  |  | 4 |  | 4 |  |  |  |  |
| group | search | 29 |  | 1 | 5 |  | 4 |  |  |  |  |
| element-instance | get | 8 | 1 | 1 | 1 |  | 1 |  |  |  |  |
| element-instance | update | 2 |  |  |  |  |  |  |  |  |  |
| element-instance | search | 57 | 6 | 7 | 2 | 1 | 1 |  | 5 | 11 |  |
| element-instance | other | 7 |  |  | 1 |  | 1 |  |  |  |  |
| decision-definition | get | 14 |  | 2 | 2 |  | 2 |  |  |  |  |
| decision-definition | update | 9 |  | 1 | 1 |  |  |  |  |  |  |
| decision-definition | search | 24 |  | 1 | 1 |  |  |  | 5 | 1 |  |
| batch-operation | get | 6 | 1 |  | 1 |  | 1 |  |  |  |  |
| batch-operation | update | 8 |  | 1 |  |  |  |  |  |  |  |
| batch-operation | delete | 7 |  | 1 |  |  |  |  |  |  |  |
| batch-operation | search | 37 | 5 | 7 | 2 |  |  |  | 3 | 4 |  |
| user | create | 12 | 4 | 5 | 1 |  |  | 1 |  |  |  |
| user | get | 5 |  |  | 1 |  | 1 |  |  |  |  |
| user | update | 6 | 3 |  | 1 |  | 1 |  |  |  |  |
| user | delete | 3 |  |  | 1 |  | 1 |  |  |  |  |
| user | search | 9 |  | 1 | 1 |  |  |  |  |  |  |
| global-task-listener | create | 11 | 3 | 4 | 1 | 2 |  | 1 |  |  |  |
| global-task-listener | update | 7 | 2 | 3 | 1 |  | 1 |  |  |  |  |
| global-task-listener | delete | 7 | 1 |  | 1 |  | 2 | 1 |  |  | 1 |
| global-task-listener | search | 20 |  | 2 | 1 |  |  |  | 7 |  |  |
| process-definition | get | 26 | 4 | 3 | 3 |  | 3 |  |  |  |  |
| process-definition | search | 36 |  | 6 | 2 |  |  |  | 5 | 6 |  |
| incident | get | 8 | 1 | 1 | 1 |  | 1 |  |  |  |  |
| incident | update | 24 | 4 | 4 | 2 | 1 | 1 |  | 3 | 2 |  |
| incident | search | 28 | 6 | 4 | 2 | 1 |  |  | 1 | 2 |  |
| mapping-rule | create | 7 |  | 4 | 1 |  |  | 1 |  |  |  |
| mapping-rule | get | 5 |  |  | 1 |  | 1 |  |  |  |  |
| mapping-rule | update | 6 |  | 3 | 1 |  | 1 |  |  |  |  |
| mapping-rule | delete | 3 |  |  | 1 |  | 1 |  |  |  |  |
| mapping-rule | search | 9 |  |  | 1 |  |  |  |  |  |  |
| document | create | 16 |  | 6 | 3 |  | 1 |  |  |  |  |
| document | get | 6 |  |  | 1 |  | 1 |  |  |  |  |
| document | delete | 3 |  |  | 1 |  | 1 |  |  |  |  |
| decision-instance | create | 8 |  | 3 | 1 | 1 | 1 |  |  | 2 |  |
| decision-instance | get | 8 | 1 | 1 | 1 |  | 1 |  |  |  |  |
| decision-instance | delete | 5 | 1 |  | 1 | 1 | 1 |  |  |  |  |
| decision-instance | search | 18 | 1 | 2 | 1 |  |  |  |  | 5 |  |
| message | create | 4 |  | 2 | 1 |  |  |  |  |  |  |
| message | update | 5 |  | 2 | 1 |  | 1 |  |  |  |  |
| message | search | 30 | 4 | 5 | 2 | 1 |  |  | 2 | 3 |  |
| resource | create | 8 | 6 | 1 | 1 |  |  |  |  |  |  |
| resource | get | 12 | 2 |  | 2 |  | 2 |  |  |  |  |
| resource | delete | 5 | 1 | 2 | 1 |  | 1 |  |  |  |  |
| audit-log | get | 8 | 1 |  | 1 | 1 | 1 |  |  |  |  |
| audit-log | search | 29 | 1 | 5 | 1 | 1 |  |  | 6 | 4 |  |
| audit-log | parameterized | 2 |  |  |  |  |  |  |  |  |  |
| decision-requirements | get | 16 | 2 | 2 | 2 |  | 2 |  |  |  |  |
| decision-requirements | search | 13 | 3 | 2 | 1 |  |  |  |  | 1 |  |
| variable | get | 8 | 1 | 1 | 1 |  | 1 |  |  |  |  |
| variable | search | 23 | 1 | 3 | 1 |  |  |  | 5 | 3 |  |
| conditional | update | 14 | 1 | 5 | 1 |  |  |  |  |  |  |
| optimize | search | 1 |  |  |  |  |  |  |  | 1 |  |
| optimize | other | 5 |  |  |  |  |  |  |  |  |  |
| signal | create | 5 | 2 | 1 | 1 |  |  |  |  |  |  |
| authentication | get | 5 |  |  | 1 |  |  |  |  |  |  |
| usage-metrics | get | 8 | 1 | 1 | 1 | 1 |  |  |  |  |  |
| cluster | get | 4 |  |  | 1 |  |  |  |  |  |  |
| expression | parameterized | 3 |  |  |  |  |  |  |  |  |  |
| clock | create | 2 |  | 1 |  |  |  |  |  |  |  |
| clock | delete | 1 |  |  |  |  |  |  |  |  |  |
| license | get | 3 |  |  | 1 |  |  |  |  |  |  |
| message-subscriptions | search | 3 |  |  | 1 |  |  |  |  |  |  |
