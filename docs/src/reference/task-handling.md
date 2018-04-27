# Task Handling

TODO: Concepts of task handling via subscriptions, lifecycle, retries, incidents, etc.

## Incidents

In the event that a payload mapping cannot be applied, e.g. because it references non-existing properties, then an *incident* is raised. An incident marks the event that workflow instance execution cannot continue without administrative repair. Incidents are published as events to the topic and can be received via topic subscriptions. In the case of payload-related incidents, updating the corresponding activity instance payload resolves the incident.