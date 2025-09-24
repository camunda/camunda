# Rdbms Module

## Logging

By default, we set all loggers that could log sensitive information (Records, SQLs, Queue Items) to INFO level.
To enable debug logging for these loggers, it is not enough to enable logging for rdbms in log4j2.xml.

They are set in application.properties, but you can override them via environment variables.

For exported Records:

```
logging.level.io.camunda.exporter.rdbms.RdbmsExporter=TRACE
```

For executed SQLs + Parameters:

```
logging.level.io.camunda.db.rdbms.sql=DEBUG
```

For Execution Queue Items:

```
logging.level.io.camunda.db.rdbms.write.queue=TRACE
```

