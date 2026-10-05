# OC API v2 — Coverage matrix (entity × operation × variant)

Total test declarations: **1001** across **33** entities.

`total` counts **test declarations** for (entity, operation). Variant columns count **labels** — a single test can match multiple variants, so variant counts can sum to more than `total`.

Legend: ✓ = at least 1, blank = 0.

## At-a-glance presence (✓ = ≥1 test)

| entity | op | total | happy | bad-req | 401 | 403 | 404 | conflict | pagin/sort | filter | absence |
|--|--|--:|--|--|--|--|--|--|--|--|--|
| process-instance | create | 35 | ✓ | ✓ | ✓ |  | ✓ |  |  | ✓ |  |
| process-instance | get | 14 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| process-instance | update | 16 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |  |  |  |
| process-instance | delete | 18 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |  | ✓ |  |
| process-instance | search | 17 | ✓ | ✓ | ✓ |  | ✓ |  | ✓ | ✓ |  |
| process-instance | other | 13 | ✓ | ✓ |  |  |  | ✓ |  |  | ✓ |
| tenant | create | 11 |  | ✓ | ✓ |  |  | ✓ |  |  |  |
| tenant | get | 3 |  |  | ✓ |  | ✓ |  |  |  |  |
| tenant | update | 26 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| tenant | delete | 23 | ✓ |  | ✓ |  | ✓ |  |  |  |  |
| tenant | search | 24 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| authorization | create | 44 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |  |  |  |
| authorization | get | 4 | ✓ |  | ✓ | ✓ | ✓ |  |  |  |  |
| authorization | update | 12 | ✓ | ✓ | ✓ | ✓ | ✓ |  |  |  |  |
| authorization | delete | 7 | ✓ | ✓ | ✓ | ✓ | ✓ |  |  |  |  |
| authorization | search | 11 | ✓ | ✓ | ✓ | ✓ |  |  | ✓ | ✓ |  |
| role | create | 10 |  | ✓ | ✓ |  |  | ✓ |  |  |  |
| role | get | 3 |  |  | ✓ |  | ✓ |  |  |  |  |
| role | update | 23 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| role | delete | 18 |  |  | ✓ |  | ✓ |  |  |  |  |
| role | search | 24 |  | ✓ | ✓ |  | ✓ |  |  | ✓ |  |
| cluster-variables | create | 10 |  | ✓ | ✓ |  | ✓ |  |  |  |  |
| cluster-variables | get | 6 |  |  | ✓ |  | ✓ |  |  |  |  |
| cluster-variables | update | 30 |  | ✓ | ✓ |  | ✓ |  |  |  |  |
| cluster-variables | delete | 6 |  |  | ✓ |  | ✓ |  |  |  |  |
| cluster-variables | search | 15 | ✓ | ✓ | ✓ |  |  |  | ✓ | ✓ |  |
| job | create | 1 |  |  |  |  |  |  |  |  |  |
| job | get | 9 | ✓ | ✓ | ✓ | ✓ |  |  |  |  |  |
| job | update | 15 | ✓ | ✓ |  |  | ✓ | ✓ |  |  |  |
| job | search | 21 | ✓ | ✓ | ✓ | ✓ | ✓ |  | ✓ | ✓ |  |
| job | other | 11 |  | ✓ | ✓ |  |  |  |  |  |  |
| user-task | get | 8 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| user-task | update | 22 | ✓ | ✓ | ✓ |  | ✓ | ✓ |  |  |  |
| user-task | delete | 7 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| user-task | search | 14 | ✓ | ✓ | ✓ |  |  |  | ✓ | ✓ |  |
| group | create | 6 |  | ✓ | ✓ |  |  | ✓ |  |  |  |
| group | get | 3 |  |  | ✓ |  | ✓ |  |  |  |  |
| group | update | 8 |  | ✓ | ✓ |  | ✓ |  |  |  |  |
| group | delete | 12 |  |  | ✓ |  | ✓ |  |  |  |  |
| group | search | 19 |  | ✓ | ✓ |  | ✓ |  |  |  |  |
| element-instance | get | 4 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| element-instance | update | 2 |  |  |  |  |  |  |  |  |  |
| element-instance | search | 24 | ✓ | ✓ | ✓ | ✓ | ✓ |  | ✓ | ✓ |  |
| element-instance | other | 5 |  |  | ✓ |  | ✓ |  |  |  |  |
| decision-definition | get | 8 |  | ✓ | ✓ |  | ✓ |  |  |  |  |
| decision-definition | update | 9 |  | ✓ | ✓ |  |  |  |  |  |  |
| decision-definition | search | 16 |  | ✓ | ✓ |  |  |  | ✓ | ✓ |  |
| batch-operation | get | 3 | ✓ |  | ✓ |  | ✓ |  |  |  |  |
| batch-operation | update | 7 |  | ✓ | ✓ |  | ✓ |  |  |  |  |
| batch-operation | delete | 6 |  | ✓ | ✓ |  | ✓ |  |  |  |  |
| batch-operation | search | 16 | ✓ | ✓ | ✓ |  |  |  | ✓ | ✓ |  |
| user | create | 11 | ✓ | ✓ | ✓ |  |  | ✓ |  |  |  |
| user | get | 3 |  |  | ✓ |  | ✓ |  |  |  |  |
| user | update | 6 | ✓ |  | ✓ |  | ✓ |  |  |  |  |
| user | delete | 3 |  |  | ✓ |  | ✓ |  |  |  |  |
| user | search | 7 |  | ✓ | ✓ |  |  |  |  |  |  |
| global-task-listener | create | 11 | ✓ | ✓ | ✓ | ✓ |  | ✓ |  |  |  |
| global-task-listener | update | 7 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| global-task-listener | delete | 4 | ✓ |  | ✓ |  | ✓ | ✓ |  |  | ✓ |
| global-task-listener | search | 8 |  | ✓ | ✓ |  |  |  | ✓ |  |  |
| process-definition | get | 13 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| process-definition | search | 17 |  | ✓ | ✓ |  |  |  | ✓ | ✓ |  |
| incident | get | 4 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| incident | update | 5 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| incident | search | 19 | ✓ | ✓ | ✓ | ✓ |  |  | ✓ | ✓ |  |
| mapping-rule | create | 7 |  | ✓ | ✓ |  |  | ✓ |  |  |  |
| mapping-rule | get | 3 |  |  | ✓ |  | ✓ |  |  |  |  |
| mapping-rule | update | 6 |  | ✓ | ✓ |  | ✓ |  |  |  |  |
| mapping-rule | delete | 3 |  |  | ✓ |  | ✓ |  |  |  |  |
| mapping-rule | search | 8 |  |  | ✓ |  |  |  |  |  |  |
| document | create | 16 |  | ✓ | ✓ | ✓ | ✓ |  |  |  |  |
| document | get | 4 |  | ✓ | ✓ |  | ✓ |  |  |  |  |
| document | delete | 3 |  |  | ✓ |  | ✓ |  |  |  |  |
| decision-instance | create | 5 |  | ✓ | ✓ | ✓ | ✓ |  |  | ✓ |  |
| decision-instance | get | 4 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| decision-instance | delete | 5 | ✓ |  | ✓ | ✓ | ✓ |  |  |  |  |
| decision-instance | search | 9 | ✓ | ✓ | ✓ |  |  |  |  | ✓ |  |
| message | create | 4 |  | ✓ | ✓ |  |  |  |  |  |  |
| message | update | 5 |  | ✓ | ✓ |  | ✓ |  |  |  |  |
| message | search | 13 | ✓ | ✓ | ✓ | ✓ |  |  | ✓ | ✓ |  |
| resource | create | 8 | ✓ | ✓ | ✓ |  |  |  |  |  |  |
| resource | get | 6 | ✓ |  | ✓ |  | ✓ |  |  |  |  |
| resource | delete | 5 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| audit-log | get | 4 | ✓ |  | ✓ | ✓ | ✓ |  |  |  |  |
| audit-log | search | 11 | ✓ | ✓ | ✓ | ✓ |  |  | ✓ | ✓ |  |
| audit-log | parameterized | 2 |  |  |  |  |  |  |  |  |  |
| decision-requirements | get | 8 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| decision-requirements | search | 6 | ✓ | ✓ | ✓ |  |  |  |  | ✓ |  |
| variable | get | 4 | ✓ | ✓ | ✓ |  | ✓ |  |  |  |  |
| variable | search | 10 | ✓ | ✓ | ✓ |  |  |  | ✓ | ✓ |  |
| conditional | update | 12 | ✓ | ✓ | ✓ |  |  |  |  |  |  |
| conditional | search | 1 |  |  |  |  |  |  |  |  |  |
| optimize | search | 1 |  |  |  |  |  |  |  | ✓ |  |
| optimize | other | 5 |  |  |  |  |  |  |  |  |  |
| signal | create | 5 | ✓ | ✓ | ✓ |  |  |  |  |  |  |
| authentication | get | 4 |  |  | ✓ |  |  |  |  |  |  |
| usage-metrics | get | 4 | ✓ | ✓ | ✓ | ✓ |  |  |  |  |  |
| cluster | get | 3 |  |  | ✓ |  |  |  |  |  |  |
| clock | create | 2 |  | ✓ |  |  |  |  |  |  |  |
| clock | delete | 1 |  |  |  |  |  |  |  |  |  |
| expression | parameterized | 3 |  |  |  |  |  |  |  |  |  |
| license | get | 2 |  |  | ✓ |  |  |  |  |  |  |
| message-subscriptions | search | 2 |  |  | ✓ |  |  |  |  |  |  |

## Counts per cell

| entity | op | total | happy | bad-req | 401 | 403 | 404 | conflict | pagin/sort | filter | absence |
|--|--|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|
| process-instance | create | 35 | 9 | 12 | 4 |  | 1 |  |  | 15 |  |
| process-instance | get | 14 | 3 | 3 | 3 |  | 1 |  |  |  |  |
| process-instance | update | 16 | 3 | 7 | 3 | 1 | 2 | 1 |  |  |  |
| process-instance | delete | 18 | 4 | 3 | 3 | 2 | 3 | 1 |  | 2 |  |
| process-instance | search | 17 | 5 | 2 | 3 |  | 1 |  | 1 | 2 |  |
| process-instance | other | 13 | 8 | 1 |  |  |  | 2 |  |  | 1 |
| tenant | create | 11 |  | 3 | 1 |  |  | 6 |  |  |  |
| tenant | get | 3 |  |  | 1 |  | 1 |  |  |  |  |
| tenant | update | 26 | 5 | 2 | 6 |  | 9 |  |  |  |  |
| tenant | delete | 23 | 2 |  | 6 |  | 11 |  |  |  |  |
| tenant | search | 24 | 2 | 1 | 6 |  | 3 |  |  |  |  |
| authorization | create | 44 | 10 | 15 | 5 | 5 | 4 | 5 |  |  |  |
| authorization | get | 4 | 1 |  | 1 | 1 | 1 |  |  |  |  |
| authorization | update | 12 | 5 | 3 | 1 | 1 | 2 |  |  |  |  |
| authorization | delete | 7 | 3 | 1 | 1 | 1 | 1 |  |  |  |  |
| authorization | search | 11 | 6 | 3 | 1 | 1 |  |  | 4 | 5 |  |
| role | create | 10 |  | 3 | 1 |  |  | 5 |  |  |  |
| role | get | 3 |  |  | 1 |  | 1 |  |  |  |  |
| role | update | 23 | 2 | 2 | 5 |  | 7 |  |  |  |  |
| role | delete | 18 |  |  | 5 |  | 9 |  |  |  |  |
| role | search | 24 |  | 1 | 5 |  | 2 |  |  | 4 |  |
| cluster-variables | create | 10 |  | 5 | 2 |  | 1 |  |  |  |  |
| cluster-variables | get | 6 |  |  | 2 |  | 2 |  |  |  |  |
| cluster-variables | update | 30 |  | 3 | 2 |  | 3 |  |  |  |  |
| cluster-variables | delete | 6 |  |  | 2 |  | 2 |  |  |  |  |
| cluster-variables | search | 15 | 1 | 2 | 1 |  |  |  | 4 | 6 |  |
| job | create | 1 |  |  |  |  |  |  |  |  |  |
| job | get | 9 | 4 | 2 | 1 | 2 |  |  |  |  |  |
| job | update | 15 | 4 | 4 |  |  | 4 | 3 |  |  |  |
| job | search | 21 | 6 | 6 | 4 | 3 | 1 |  | 1 | 2 |  |
| job | other | 11 |  | 3 | 1 |  |  |  |  |  |  |
| user-task | get | 8 | 3 | 1 | 2 |  | 2 |  |  |  |  |
| user-task | update | 22 | 10 | 5 | 3 |  | 3 | 1 |  |  |  |
| user-task | delete | 7 | 4 | 1 | 1 |  | 1 |  |  |  |  |
| user-task | search | 14 | 3 | 3 | 2 |  |  |  | 1 | 4 |  |
| group | create | 6 |  | 1 | 1 |  |  | 3 |  |  |  |
| group | get | 3 |  |  | 1 |  | 1 |  |  |  |  |
| group | update | 8 |  | 1 | 1 |  | 2 |  |  |  |  |
| group | delete | 12 |  |  | 4 |  | 4 |  |  |  |  |
| group | search | 19 |  | 1 | 5 |  | 4 |  |  |  |  |
| element-instance | get | 4 | 1 | 1 | 1 |  | 1 |  |  |  |  |
| element-instance | update | 2 |  |  |  |  |  |  |  |  |  |
| element-instance | search | 24 | 6 | 6 | 2 | 1 | 1 |  | 5 | 11 |  |
| element-instance | other | 5 |  |  | 1 |  | 1 |  |  |  |  |
| decision-definition | get | 8 |  | 2 | 2 |  | 2 |  |  |  |  |
| decision-definition | update | 9 |  | 1 | 1 |  |  |  |  |  |  |
| decision-definition | search | 16 |  | 1 | 1 |  |  |  | 5 | 1 |  |
| batch-operation | get | 3 | 1 |  | 1 |  | 1 |  |  |  |  |
| batch-operation | update | 7 |  | 1 | 1 |  | 2 |  |  |  |  |
| batch-operation | delete | 6 |  | 1 | 1 |  | 2 |  |  |  |  |
| batch-operation | search | 16 | 5 | 5 | 2 |  |  |  | 3 | 4 |  |
| user | create | 11 | 4 | 5 | 1 |  |  | 1 |  |  |  |
| user | get | 3 |  |  | 1 |  | 1 |  |  |  |  |
| user | update | 6 | 3 |  | 1 |  | 1 |  |  |  |  |
| user | delete | 3 |  |  | 1 |  | 1 |  |  |  |  |
| user | search | 7 |  | 1 | 1 |  |  |  |  |  |  |
| global-task-listener | create | 11 | 3 | 4 | 1 | 2 |  | 1 |  |  |  |
| global-task-listener | update | 7 | 2 | 3 | 1 |  | 1 |  |  |  |  |
| global-task-listener | delete | 4 | 1 |  | 1 |  | 2 | 1 |  |  | 1 |
| global-task-listener | search | 8 |  | 2 | 1 |  |  |  | 7 |  |  |
| process-definition | get | 13 | 4 | 3 | 3 |  | 3 |  |  |  |  |
| process-definition | search | 17 |  | 5 | 2 |  |  |  | 5 | 6 |  |
| incident | get | 4 | 1 | 1 | 1 |  | 1 |  |  |  |  |
| incident | update | 5 | 2 | 1 | 1 |  | 1 |  |  |  |  |
| incident | search | 19 | 8 | 4 | 3 | 2 |  |  | 4 | 4 |  |
| mapping-rule | create | 7 |  | 4 | 1 |  |  | 1 |  |  |  |
| mapping-rule | get | 3 |  |  | 1 |  | 1 |  |  |  |  |
| mapping-rule | update | 6 |  | 3 | 1 |  | 1 |  |  |  |  |
| mapping-rule | delete | 3 |  |  | 1 |  | 1 |  |  |  |  |
| mapping-rule | search | 8 |  |  | 1 |  |  |  |  |  |  |
| document | create | 16 |  | 7 | 3 | 1 | 1 |  |  |  |  |
| document | get | 4 |  | 1 | 1 |  | 1 |  |  |  |  |
| document | delete | 3 |  |  | 1 |  | 1 |  |  |  |  |
| decision-instance | create | 5 |  | 2 | 1 | 1 | 1 |  |  | 2 |  |
| decision-instance | get | 4 | 1 | 1 | 1 |  | 1 |  |  |  |  |
| decision-instance | delete | 5 | 1 |  | 1 | 1 | 1 |  |  |  |  |
| decision-instance | search | 9 | 1 | 1 | 1 |  |  |  |  | 5 |  |
| message | create | 4 |  | 2 | 1 |  |  |  |  |  |  |
| message | update | 5 |  | 2 | 1 |  | 1 |  |  |  |  |
| message | search | 13 | 4 | 4 | 2 | 1 |  |  | 2 | 3 |  |
| resource | create | 8 | 6 | 1 | 1 |  |  |  |  |  |  |
| resource | get | 6 | 2 |  | 2 |  | 2 |  |  |  |  |
| resource | delete | 5 | 1 | 2 | 1 |  | 1 |  |  |  |  |
| audit-log | get | 4 | 1 |  | 1 | 1 | 1 |  |  |  |  |
| audit-log | search | 11 | 1 | 4 | 1 | 1 |  |  | 6 | 4 |  |
| audit-log | parameterized | 2 |  |  |  |  |  |  |  |  |  |
| decision-requirements | get | 8 | 2 | 2 | 2 |  | 2 |  |  |  |  |
| decision-requirements | search | 6 | 3 | 1 | 1 |  |  |  |  | 1 |  |
| variable | get | 4 | 1 | 1 | 1 |  | 1 |  |  |  |  |
| variable | search | 10 | 1 | 3 | 1 |  |  |  | 5 | 3 |  |
| conditional | update | 12 | 1 | 4 | 1 |  |  |  |  |  |  |
| conditional | search | 1 |  |  |  |  |  |  |  |  |  |
| optimize | search | 1 |  |  |  |  |  |  |  | 1 |  |
| optimize | other | 5 |  |  |  |  |  |  |  |  |  |
| signal | create | 5 | 2 | 1 | 1 |  |  |  |  |  |  |
| authentication | get | 4 |  |  | 1 |  |  |  |  |  |  |
| usage-metrics | get | 4 | 1 | 1 | 1 | 1 |  |  |  |  |  |
| cluster | get | 3 |  |  | 1 |  |  |  |  |  |  |
| clock | create | 2 |  | 1 |  |  |  |  |  |  |  |
| clock | delete | 1 |  |  |  |  |  |  |  |  |  |
| expression | parameterized | 3 |  |  |  |  |  |  |  |  |  |
| license | get | 2 |  |  | 1 |  |  |  |  |  |  |
| message-subscriptions | search | 2 |  |  | 1 |  |  |  |  |  |  |
