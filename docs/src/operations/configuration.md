# Configuration

Zeebe can be configured through configuration files, environment variables or a mix of both. If both configuration files and environment variables are used, then the latter take precedence over configuration files.

The configuration will be applied during startup of Zeebe. It is not possible to change the configuration at runtime.

## Default Configuration
The default configuration is located in `config/application.yaml`. This configuration contains the most common configuration settings for a standalone broker

> **Note**
>
> The default configuration is not suitable for a standalone gateway node. If you want to run a standalone gateway node, please have a look at `/config/gateway.yaml.template`


## Configuration file templates
We provide templates that contain all possible configuration settings, along with explanations for each setting:
* [Broker Configuration Templates](/appendix/broker-config-template.md)
* [Gateway Configuration Template](/appendix/gateway-config-template.md)

Note that these templates also include the corresponding environment variables to use for every setting.


## Editing the configuration
You can either start from scratch or start from the configuration templates listed above.

If you use a configuration template and want to uncomment certain lines, make sure to also uncomment their parent elements:

```yaml
Valid Configuration

    zeebe:
      gateway:
        network:
          # host: 0.0.0.0
          port: 26500

Invalid configuration

    # zeebe:
      # gateway:
        # network:
          # host: 0.0.0.0
          port: 26500
```

Uncommenting individual lines is a bit finicky, because YAML is sensitive to indentation. The best way to do it is to position the cursor before the `#` character and delete two characters (the dash and the space). Doing this consistently will give you a valid YAML file.

When it comes to editing individual settings two data types are worth mentioning:
* Data Sizes (e.g. `logSegmentSize`)
  * Human friendly format: `500MB` (or `KB, GB`)
  * Machine friendly format: size in bytes as long
* Timeouts/Intervals (e.g. `requestTimeout`)
  * Human friendly format: `15s` (or `m, h`)
  * Machine friendly format: either duration in milliseconds as long, or [ISO-8601 Duration](ttps://en.wikipedia.org/wiki/ISO_8601#Durations) format (e.g. `PT15S`)

## Passing configuration file to Zeebe

The configuration file can be passed to Zeebe either by adhering to naming conventions, or by explicitly specifying the configuration file through an environment variable or via a command line argument when launching Zeebe.

*Naming conventions*

Rename the configuration file to `application.yaml` and place it in the following location:
```shell script
./config/application.yaml
```

*Environment Variable*

Rename the configuration file to `*.yaml` and set an environment variable to point to the configuration file:
```shell script
export SPRING_CONFIG_LOCATION='file:./[path to config file]'
```

*Command line argument*

Rename the configuration file to `*.yaml` and add a command line argument to point to the configuration file:
```shell script
./bin/broker --spring.config.location=file:./[path to config file]

or

./bin/gateway --spring.config.location=file:./[path to config file]
```


*Misc*

Zeebe uses Spring Boot for its configuration parsing. So all other ways to [configure a Spring Boot application](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config) should also work.

## Verifying that configuration was applied
To verify that the configuration was applied, start Zeebe and look at the log.

If the configuration could be read, Zeebe will log out the effective configuration during startup:

```
17:13:13.120 [] [main] INFO  io.zeebe.broker.system - Starting broker 0 with configuration {
  "network": {
    "host": "0.0.0.0",
    "portOffset": 0,
    "maxMessageSize": {
      "bytes": 4194304
    },
    "commandApi": {
      "defaultPort": 26501,
      "host": "0.0.0.0",
      "port": 26501,
...
```

In some cases of invalid configuration Zeebe will fail to start with a warning that explains which configuration setting could not be read.
```
17:17:38.796 [] [main] ERROR org.springframework.boot.diagnostics.LoggingFailureAnalysisReporter -

***************************
APPLICATION FAILED TO START
***************************

Description:

Failed to bind properties under 'zeebe.broker.network.port-offset' to int:

    Property: zeebe.broker.network.port-offset
    Value: false
    Origin: System Environment Property "ZEEBE_BROKER_NETWORK_PORTOFFSET"
    Reason: failed to convert java.lang.String to int

Action:

Update your application's configuration
```

## Logging

Zeebe uses Log4j2 framework for logging. In the distribution and the docker image you can find the default log configuration file in `config/log4j2.xml`.

### Google Stackdriver (JSON) logging

To enable Google Stackdriver compatible JSON logging you can set the environment variable `ZEEBE_LOG_APPENDER=Stackdriver` before starting Zeebe.

### Default logging configuration

* `config/log4j2.xml` (applied by default)

```
{{#include ../../../dist/src/main/config/log4j2.xml}}
```

## Health Probes
Health probes are set to sensible defaults which cover common use cases.

For specific use cases, it might be necessary to customize health probes:
* [Gateway Health Probes](/appendix/gateway-health-probes.md)
